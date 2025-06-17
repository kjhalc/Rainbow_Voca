package com.example.englishapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishapp.data.repository.WordRepository
import com.example.englishapp.model.Word
import kotlinx.coroutines.launch

// UI에 퀴즈 질문을 표시하기 위한 데이터 클래스
// Firebase에서 가져온 Word 모델을 기반으로 UI에 필요한 추가 정보(옵션, 정답 인덱스, 진행률)를 포함
data class QuizQuestionDisplay(
    val word: Word, // 원본 단어 데이터 (질문 대상)
    val options: List<String>, // 4지선다 선택지
    val correctIndex: Int, // 'options' 리스트에서 정답이 위치한 인덱스
    val questionNumber: Int, // 현재 문제 번호
    val totalQuestions: Int // 전체 문제 수
)

// "10분후 복습: 및 "누적 복습" 로직을 담당하는 뷰모델
// 사용자의 퀴즈 응답에 따라 Firebase에 저장된 학습 상태(단어의 stage, nextReviewAt 등)
// WordRepository를 통해 업데이트하는 핵심적인 백엔드 연동 로직을 수행

class WordQuizViewModel(
    private val userId: String, // 사용자 식별자 (Firebase UID)
    private val quizType: String, // 퀴즈 유형 ("10min" 또는 "cumulative")
    private val wordRepository: WordRepository // 데이터 영속성 처리 (Firebase와 통신)
) : ViewModel() {

    // LiveData 변수들: UI(Activity)가 관찰하며 상태 변경에 따라 UI를 업데이트


    // 데이터 로딩(예: 단어 목록 조회, 문제 생성) 중인지 여부를 나타냄
    // API 호출 또는 DB 작업 시 true로 설정되어 UI에 로딩 인디케이터를 표시하도록 유도
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // 현재 화면에 표시될 퀴즈 질문 데이터를 담음
    private val _currentQuestion = MutableLiveData<QuizQuestionDisplay?>()
    val currentQuestion: LiveData<QuizQuestionDisplay?> = _currentQuestion

    // 퀴즈 진행 상황을 텍스트로 표시하기 위한 데이터
    private val _quizProgressText = MutableLiveData<String>("")
    val quizProgressText: LiveData<String> = _quizProgressText


    // 퀴즈 세션이 모두 종료되었는지 여부
    // true가 되면 액티비티는 결과 처리를 하고 화면을 종료
    private val _isQuizFinished = MutableLiveData<Boolean>(false)
    val isQuizFinished: LiveData<Boolean> = _isQuizFinished

    // 사용자에게 간단한 메시지(정보, 경고 등)을 Toast 형태로 전달
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // 정답/오답 여부와 함께 사용자에게 보여줄 피드백 메시지 전달
    private val _showAnswerFeedback = MutableLiveData<Pair<Boolean, String>?>() // Pair<isCorrect, feedbackMessage>
    val showAnswerFeedback: LiveData<Pair<Boolean, String>?> = _showAnswerFeedback


    // 현재 퀴즈 세션 동안 발생한 모든 DB 작업(예: 단어 상태 업데이트)이 성공적으로 완료되었는지 여부
    // Activity가 결과를 처리할 때 이 값을 참조하여 성공/실패를 결정
    private val _wasQuizSuccessfulOverall = MutableLiveData<Boolean>(false) // DB 작업 등 전체 과정 성공 여부
    val wasQuizSuccessfulOverall: LiveData<Boolean> = _wasQuizSuccessfulOverall


    // ViewModel 내부 상태 변수
    private var quizWords: List<Word> = emptyList() // 현재 퀴즈 세션의 대상 단어 목록 (WordRepository에서 가져옴)
    private var quizQuestions: List<QuizQuestionDisplay> = emptyList() // 생성된 전체 퀴즈 문제 목록
    private var currentQuestionIndex: Int = 0  // 현재 풀고 있는 문제의 인덱스 (quizQuestions 리스트 기준)
    private var correctCount: Int = 0 // 맞춘 문제 수
    private var allDbOperationsSuccessful: Boolean = true // 현재 퀴즈 세션 내 모든 Firestore 업데이트 성공 여부 플래그

    companion object {
        private const val TAG = "WordQuizViewModel"
        private const val NUMBER_OF_OPTIONS = 4
    }

    // 퀴즈 시작시 호출되는 함수 (단어 로드 및 문제 생성)
    // 퀴즈 유형(10min/cumulative)에 따라 적절한 단어 목록을 WordRepository를 통해 Firebase에서 조회
    fun loadQuiz() {
        // 이미 로드 중이거나, 퀴즈 질문이 이미 생성되어 있고 아직 끝나지 않았다면 새로 로드하지 않음
        if (_isLoading.value == true || (quizQuestions.isNotEmpty() && _isQuizFinished.value == false) ) {
            // 화면 회전 등으로 UI만 재생성된 경우, 현재 질문 다시 표시
            if (quizQuestions.isNotEmpty() && _currentQuestion.value == null && currentQuestionIndex < quizQuestions.size) {
                displayCurrentQuestion()
            }
            return
        }

        _isLoading.value = true // 데이터 로딩 시작을 UI에 알림
        allDbOperationsSuccessful = true // 퀴즈 세션 시작 시 초기화
        correctCount = 0
        currentQuestionIndex = 0
        _isQuizFinished.value = false // 퀴즈 시작 시 완료 상태 초기화
        _wasQuizSuccessfulOverall.value = false // 전체 성공 여부 초기화

        viewModelScope.launch { // 비동기 작업을 위한 코루틴 실행
            try {
                Log.i(TAG, "Loading quiz. Type: $quizType, User: $userId")

                // quizType에 따라 Repository의 다른 API(Firestore 쿼리)를 호출
                // API 라우팅과 유사한 역할
                quizWords = when (quizType) {
                    "10min" -> wordRepository.getWordsForTenMinReview(userId)
                    "cumulative" -> wordRepository.getWordsForCumulativeReview(userId)
                    else -> {
                        Log.e(TAG, "Unknown quiz type: $quizType"); emptyList()
                    }
                }

                // 조회된 단어가 없는 경우 -> 비정상적인 시나리오
                if (quizWords.isEmpty()) {
                    val message = when (quizType) {
                        "10min" -> "10분 후 복습할 단어가 없습니다."
                        "cumulative" -> "오늘 누적 복습할 단어가 없습니다."
                        else -> "퀴즈 대상 단어가 없습니다."
                    }
                    _toastMessage.value = message
                    _wasQuizSuccessfulOverall.value = true // 단어가 없는 것도 '정상적'인 완료로 간주
                    finishQuizInternally() // 내부적으로 퀴즈 종료 처리
                    Log.i(TAG, "No words to quiz for type: $quizType. Quiz session finished.")
                } else {
                    // 조회된 단어 목록(quizWords)을 기반으로 실제 퀴즈 문제(QuizQuestionDisplay)를 생성
                    // 이때 오답 선택지를 생성하기 위해 DB 조회
                    quizQuestions = generateQuizQuestions(quizWords) // suspend 함수
                    if (quizQuestions.isNotEmpty()) {
                        currentQuestionIndex = 0
                        displayCurrentQuestion() // 첫 문제 표시
                        Log.i(TAG, "Generated ${quizQuestions.size} quiz questions for $quizType quiz.")
                    } else {
                        // 문제 생성에 실패한 경우
                        _toastMessage.value = "퀴즈 문제를 생성하지 못했습니다. (단어는 있으나 문제 생성 실패)"
                        _wasQuizSuccessfulOverall.value = false // 문제 생성 실패
                        finishQuizInternally()
                        Log.e(TAG, "Failed to generate quiz questions even though quizWords was not empty for $quizType quiz.")
                    }
                }
            } catch (e: Exception) { // Repository에서 데이터 조회 중 예외 발생 시
                Log.e(TAG, "Error loading quiz data for $quizType quiz", e)
                _toastMessage.value = "퀴즈 로딩 중 오류 발생: ${e.message}" // 사용자에게 오류 알림
                _wasQuizSuccessfulOverall.value = false
                finishQuizInternally()
            } finally {
                _isLoading.value = false // 데이터 로딩 완료 (성공/실패 무관)
            }
        }
    }


    // 퀴즈 문제 목록을 생성하는 내부 함수 (오답 선택지 포함)
    // 오답 선택지 생성 전략
    // 1. 현재 퀴즈 묶음 내 다른 단어의 뜻 활용
    // 2. 부족하면 전체 단어 목록(words 컬렉션)에서 랜덤 뜻 활용 (DB 추가 조회)
    // 3. 그래도 부족하면 더미 뜻 활용
    // 서버에서 이 로직을 수행한다면, 클라이언트는 더 단순해지고, 오답 품질 관리가 용이
    private suspend fun generateQuizQuestions(words: List<Word>): List<QuizQuestionDisplay> {
        val questions = mutableListOf<QuizQuestionDisplay>()
        // 현재 퀴즈에 포함된 모든 단어의 뜻 목록 (중복 제거, 오답 생성 시 제외 조건으로 사용)
        val allWordsInBatchMeanings = words.mapNotNull { it.word_mean }

        for ((index, correctWord) in words.withIndex()) {
            val wordDocId = correctWord.docId
            val currentWordText = correctWord.word_text ?: "알 수 없는 단어"
            // 데이터 무결성 체크. 단어 ID나 뜻이 없는 경우 해당 단어는 퀴즈에서 제외
            if (wordDocId.isNullOrEmpty() || correctWord.word_mean.isNullOrEmpty()) {
                Log.w(TAG, "Word '$currentWordText' (ID: $wordDocId) has no valid docId or meaning, skipping question generation.")
                continue
            }

            val correctAnswerMeaning = correctWord.word_mean!! // 정답 뜻
            val wrongOptions = mutableListOf<String>() // 오답 선택지 리스트

            // 전략 1: 현재 퀴즈 세트에 포함된 다른 단어들의 뜻을 오답으로 사용
            words.filter { it.docId != wordDocId && !it.word_mean.isNullOrEmpty() && it.word_mean != correctAnswerMeaning }
                .shuffled() // 랜덤으로 섞기
                .take(NUMBER_OF_OPTIONS - 1) // 필요한 만큼 가져오기(정답-1)
                .forEach { wrongOptions.add(it.word_mean!!) }

            // 전략 2: 여전히 오답이 부족하면, 전체 단어 목록에서 랜덤으로 가져옴
            val neededMoreOptions = (NUMBER_OF_OPTIONS - 1) - wrongOptions.size
            if (neededMoreOptions > 0) {
                // 제외할 뜻 목록: 정답 뜻, 현재 퀴즈 묶음의 모든 뜻, 이미 선택된 오답 뜻
                val meaningsToExclude = mutableListOf(correctAnswerMeaning)
                meaningsToExclude.addAll(allWordsInBatchMeanings) // 현재 묶음 뜻도 제외
                meaningsToExclude.addAll(wrongOptions) // 이미 고른 오답도 제외

                Log.d(TAG, "For word '$currentWordText', needing $neededMoreOptions more random options. Excluding ${meaningsToExclude.distinct().size} meanings.")
                val randomMeanings = wordRepository.getRandomWordMeanings(neededMoreOptions, meaningsToExclude.distinct())
                wrongOptions.addAll(randomMeanings)
                Log.d(TAG, "Fetched ${randomMeanings.size} additional random meanings for '$currentWordText'. Wrong options now: $wrongOptions")
            }

            // 전략 3: 그래도 오답이 부족하면 "다른 뜻 N" 형태의 더미 오답 추가
            var dummyCounter = 1
            while (wrongOptions.size < NUMBER_OF_OPTIONS - 1) {
                val dummyMeaning = "다른 뜻 ${dummyCounter++}"
                if (dummyMeaning != correctAnswerMeaning && !wrongOptions.contains(dummyMeaning)) {
                    wrongOptions.add(dummyMeaning)
                }
                if (dummyCounter > 10) { // 무한 루프 방지
                    Log.w(TAG, "Could not generate enough unique wrong options for '$currentWordText', even with dummies.")
                    break
                }
            }

            // 오답 선택지 충분히 생성 X -> 틀린 시나리오
            if (wrongOptions.size < NUMBER_OF_OPTIONS - 1) {
                Log.e(TAG, "Still not enough wrong options for word '$currentWordText'. Skipping this question. Wrong options: $wrongOptions")
                continue
            }

            // 정답 1개 + 오답 3개를 합쳐 최종 선택지 목록 생성 후 섞기
            val finalOptions = (wrongOptions.take(NUMBER_OF_OPTIONS - 1) + correctAnswerMeaning).shuffled()
            val correctIndexInOptions = finalOptions.indexOf(correctAnswerMeaning)

            // 유효성 검증: 정답 인덱스가 유효하고, 선택지 개수가 정확히 4개인지 확인
            if (correctIndexInOptions != -1 && finalOptions.size == NUMBER_OF_OPTIONS) {
                questions.add(
                    QuizQuestionDisplay(
                        word = correctWord,
                        options = finalOptions,
                        correctIndex = correctIndexInOptions,
                        questionNumber = index + 1, // 실제 표시용 번호는 displayCurrentQuestion에서 다시 설정
                        totalQuestions = words.size // 실제 표시용 전체 개수도 displayCurrentQuestion에서 다시 설정
                    )
                )
            } else {
                // 문제 생성 로직 결함 확인용 로그
                Log.e(TAG, "Failed to generate valid question options for '$currentWordText'. Options: $finalOptions, CorrectIndex: $correctIndexInOptions, OptionsSize: ${finalOptions.size}")
            }
        }
        Log.d(TAG, "Generated ${questions.size} questions from ${words.size} input words. Returning shuffled questions.")
        return questions.shuffled() // 최종 문제 순서도 섞기
    }

    // 현재 문제(currentQuestionIndex에 해당하는)를 UI에 표시하도록 LiveData 업데이트
    private fun displayCurrentQuestion() {
        if (currentQuestionIndex < quizQuestions.size) {
            val question = quizQuestions[currentQuestionIndex]
            _currentQuestion.value = question.copy( // UI 표시는 1부터 시작하도록 번호 조정
                questionNumber = currentQuestionIndex + 1,
                totalQuestions = quizQuestions.size
            )
            _quizProgressText.value = "${currentQuestionIndex + 1}/${quizQuestions.size}"
            Log.d(TAG, "Displaying question ${currentQuestionIndex + 1}/${quizQuestions.size}: ${question.word.word_text}")
        } else { // 모든 문제를 다 풀었다면
            Log.d(TAG, "No more questions to display. Current index: $currentQuestionIndex, Total quiz questions: ${quizQuestions.size}. Finishing quiz.")
            finishQuizInternally() // 퀴즈 종료 로직 호출
        }
    }

    // 사용자가 선택지를 눌러 답을 제출 했을 때 호출되는 함수
    // 이 함수는 사용자의 응답(정답/오답)에 따라 핵심적인 DB 업데이트 로직을 수행
    // 퀴즈 유형(10min/cumulative)과 정답 여부에 따라 단어의 학습 상태(stage, nextReviewAt 등)를 변경
    fun submitAnswer(selectedIndex: Int) {

        // 로딩 중이거나, 이미 모든 문제를 풀었거나, 퀴즈가 종료된 상태에서는 응답 처리 안 함
        if (_isLoading.value == true || currentQuestionIndex >= quizQuestions.size || _isQuizFinished.value == true) {
            Log.w(TAG, "Submit answer called when not ready. isLoading=${_isLoading.value}, index=$currentQuestionIndex, finished=${_isQuizFinished.value}")
            return
        }

        // 현재 질문 정보 가져오기
        val questionDisplay = _currentQuestion.value ?: run {
            Log.e(TAG, "Submit answer called but current question is null.")
            return
        }
        val currentWord = questionDisplay.word // 현재 문제의 단어 객체
        val wordDocId = currentWord.docId // Firestore 문서 ID

        // Firestore 문서 ID가 없는 단어는 DB 업데이트가 불가능하므로 오류 처리
        if (wordDocId.isNullOrEmpty()) {
            Log.e(TAG, "Cannot process answer. Word has no valid docId: ${currentWord.word_text}")
            _toastMessage.value = "답변 처리 중 오류 (단어 정보 없음)"
            allDbOperationsSuccessful = false // DB 작업 실패로 간주
            // 오류가 있어도 다음 문제로 넘어가도록 피드백 후 이동 처리
            _showAnswerFeedback.value = Pair(false, "단어 정보 오류")
            return
        }

        _isLoading.value = true // 사용자 입력 처리 중 로딩 상태(DB 업데이트 작업)
        val isCorrect = selectedIndex == questionDisplay.correctIndex // 정답 여부

        if (isCorrect) {
            correctCount++ // 맞춘 문제 수 증가
            Log.d(TAG, "Correct answer for: ${currentWord.word_text} (ID: $wordDocId)")
        } else {
            Log.d(TAG, "Incorrect answer for: ${currentWord.word_text} (ID: $wordDocId)")
        }

        viewModelScope.launch { // DB 업데이트 비동기 처리
            var operationSuccessThisWord = false // 현재 단어에 대한 DB 작업 성공 여부
            try {
                // 백엔드 관점: 정답/오답 및 _quizType_에 따라 _WordRepository_의 각기 다른 함수를 호출
                // Firestore의 단어 상태를 업데이트
                // 이 로직은 서버 API의 엔드포인트 분기와 유사

                // 정답일 경우
                operationSuccessThisWord = if (isCorrect) {
                    when (quizType) {
                        "10min" -> wordRepository.moveTenMinReviewWordToNextStage(userId, wordDocId) // stage 0 -> 1, nextReviewAt 설정

                        // 누적 복습 정답 시, 현재 단어의 reviewWord 정보를 가져와서 stage를 업데이트
                        "cumulative" -> {
                            val reviewWordState = wordRepository.getReviewWord(userId, wordDocId)
                            if (reviewWordState != null) {
                                wordRepository.updateCumulativeReviewWordOnCorrect(userId, wordDocId, reviewWordState)
                            } else {
                                // 누적 복습 단어는 당연히 review_words에 존재해야함
                                Log.e(TAG, "Could not find ReviewWord state for $wordDocId to update on correct.")
                                false // DB 작업 실패
                            }
                        }
                        else -> { Log.e(TAG, "Unknown quizType '$quizType' in submitAnswer."); false }
                    }
                } else { // 오답일 경우
                    when (quizType) {
                        // review_words에서 삭제, individual_states로 이동 및 우선순위 증가
                        "10min" -> wordRepository.moveTenMinReviewWordToIndividualStateOnIncorrect(userId, wordDocId)
                        "cumulative" -> wordRepository.moveCumulativeReviewWordToIndividualStateOnIncorrect(userId, wordDocId)
                        else -> { Log.e(TAG, "Unknown quizType '$quizType' in submitAnswer."); false }
                    }
                }

                // 개별 단어에 대한 DB 작업 성공/실패 여부 기록
                if (!operationSuccessThisWord) {
                    allDbOperationsSuccessful = false // 하나라도 실패하면 전체 세션 DB 작업은 실패로 간주
                    _toastMessage.value = "'${currentWord.word_text}' 단어 결과 저장 실패"
                    Log.e(TAG, "Failed to update Firestore for word: ${currentWord.word_text} (ID: $wordDocId), isCorrect: $isCorrect")
                } else {
                    Log.d(TAG, "Successfully updated Firestore for word: ${currentWord.word_text} (ID: $wordDocId), isCorrect: $isCorrect")
                }
            } catch (e: Exception) { // Repository 작업 중 예외 발생 시 (네트워크, Firestore 규칙 등)
                Log.e(TAG, "Error processing answer in repository for word: ${currentWord.word_text} (ID: $wordDocId)", e)
                allDbOperationsSuccessful = false
                _toastMessage.value = "단어 결과 저장 중 오류: ${e.message}"
            } finally {
                _isLoading.value = false // DB 작업 완료 (성공/실패 무관)

                // 사용자에게 정답/오답 피드백 메시지 전달 요청
                val feedbackMessage = if (isCorrect) "정답입니다!" else "오답입니다. 정답: ${currentWord.word_mean}"
                _showAnswerFeedback.value = Pair(isCorrect, feedbackMessage) // 피드백 표시 요청
            }
        }
    }

    // Activity에서 사용자에게 정/오답 피드백을 보여준 후 호출하는 함수
    fun moveToNextQuestionAfterFeedback() {
        _showAnswerFeedback.value = null // 피드백 표시 완료 상태로 변경
        // 다음 문제로 이동
        currentQuestionIndex++
        displayCurrentQuestion()
    }

    // 퀴즈 세션 종료 처리 (내부 호출용)
    private fun finishQuizInternally() {
        if (_isQuizFinished.value == true) return // 중복 호출 방지

        val totalQuestions = quizWords.size // quizQuestions가 아닌 quizWords 기준으로 총 개수 판단
        val accuracy = if (totalQuestions > 0) (correctCount * 100 / totalQuestions) else 0

        Log.i(TAG, "Quiz finished internally for user $userId. Type: $quizType, Correct: $correctCount/$totalQuestions ($accuracy%), All DB operations successful: $allDbOperationsSuccessful")


        // 최종 DB 작업 성공 여부를 _wasQuizSuccessfulOverall LiveData에 반영
        // Activity는 이 값을 보고 setResult를 결정
        _wasQuizSuccessfulOverall.value = allDbOperationsSuccessful
        _isQuizFinished.value = true // LiveData 업데이트하여 Activity에 알림

        // 🔽 여기에 추가
        saveCorrectWordsForAiReading()

        // 최종 결과 메시지는 Activity에서 setResult 후 필요시 표시하거나, _toastMessage 활용 가능
        // _toastMessage.value = "퀴즈 완료! ($correctCount/$totalQuestions)"
    }

    // Toast 메시지가 UI에 표시된 후 Activity에서 호출하여 상태를 초기화
    fun onToastShown() {
        _toastMessage.value = null
    }

    // 현재 문제의 단어 텍스트 반환 (TTS 등에서 사용)
    fun getCurrentWordText(): String? {
        return _currentQuestion.value?.word?.word_text
    }

    // Activity가 종료 시점에 퀴즈 결과(맞춘 개수, 전체 개수)를 가져가기 위한 함수
    fun getQuizResults(): Pair<Int, Int> { // Activity가 결과 전달 시 사용
        return Pair(correctCount, quizWords.size) // quizQuestions.size 대신 quizWords.size 사용
    }
    
    //주형추가
    fun saveCorrectWordsForAiReading() {
    val correctWordIds = quizQuestions
        .filterIndexed { index, _ -> index < currentQuestionIndex } // 푼 문제만 대상
        .filter { question ->
            val selectedCorrectly = question.correctIndex == question.options.indexOf(question.word.word_mean)
            selectedCorrectly
        }
        .mapNotNull { it.word.docId }

    if (correctWordIds.isEmpty()) {
        Log.d("WordQuizViewModel", "저장할 맞은 단어가 없습니다.")
        return
    }

    wordRepository.saveCorrectWordsForToday(userId, correctWordIds) { success ->
        if (success) {
            Log.d("WordQuizViewModel", "✅ Firestore에 맞은 단어 저장 성공")
        } else {
            Log.e("WordQuizViewModel", "❌ Firestore에 맞은 단어 저장 실패")
        }
    }
}

}
