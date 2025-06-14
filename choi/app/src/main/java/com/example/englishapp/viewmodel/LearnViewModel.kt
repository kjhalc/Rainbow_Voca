package com.example.englishapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishapp.data.repository.WordRepository
import com.example.englishapp.model.Word
import kotlinx.coroutines.launch

// UI 전용 데이터 클래스
// 서버의 Word 모델을 UI에 맞게 확장
// WordStudyActivity에서 사용할 UI 데이터 클래스
data class WordDisplayInfo(
    val word: Word, // 서버에서 가져온 원본 데이터
    val currentCycle: Int, // 현재 사이클 (1, 2, 3)
    val totalCycles: Int = 3, // 총 사이클 수
    val currentWordIndex: Int, // 현재 사이클 내에서의 단어 인덱스 (0부터)
    val totalWordsInCycle: Int, // 사이클당 총 단어 수
    val overallProgress: String // "15/30" 형태의 전체 진행률
)

class LearnViewModel(
    private val userId: String, // Firebase Auth UID
    private val wordRepository: WordRepository // Respository 패턴 (DI)
) : ViewModel() {

    // 세선 상태 관리
    // 메모리에 캐싱
    // 화면 회전시에도 유지 (ViewModel 특성)
    private var wordsForCurrentSession: List<Word> = emptyList()
    private var currentWordIndex: Int = 0 // 현재 사이클 내 단어 인덱스
    private var currentCycle: Int = 0 // 현재 사이클 (0, 1, 2)
    private val totalCycles: Int = 3 // 3회 반복

    // UI에 표시될 현재 단어 정보
    private val _currentWordDisplayInfo = MutableLiveData<WordDisplayInfo?>()
    val currentWordDisplayInfo: LiveData<WordDisplayInfo?> = _currentWordDisplayInfo

    // "오늘의 학습" 세션 전체 완료 여부 (3사이클 모두 완료) - 세션 완료 플래그
    // 액티비티 종료 시점 결정
    private val _isTodayLearningSessionComplete = MutableLiveData<Boolean>(false)
    val isTodayLearningSessionComplete: LiveData<Boolean> = _isTodayLearningSessionComplete

    // "오늘의 학습"에서 학습한 단어들의 docId 목록 (10분 후 복습 대상)
    // Firebase에 저장될 docId 목록, 세션 완료 후 설정됨
    private val _learnedWordDocIdsForQuiz = MutableLiveData<List<String>>()
    val learnedWordDocIdsForQuiz: LiveData<List<String>> = _learnedWordDocIdsForQuiz

    // 로딩용
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // 에러용
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    companion object {
        private const val TAG = "LearnViewModel" // 디버깅/로깅용
    }

    fun startTodayLearningSession(dailyGoalFromActivity: Int) {
        // 중복 요청 방지, 멱등성(Idempotency) 보장
        if (_isLoading.value == true || (_isTodayLearningSessionComplete.value == false && wordsForCurrentSession.isNotEmpty())) {
            // 이미 세션이 진행 중이거나 로딩 중이면 새로 시작하지 않음
            if (_isLoading.value == false && wordsForCurrentSession.isNotEmpty() && currentWordIndex != -1) {
                updateCurrentWordDisplay() // 현재 상태로 UI 업데이트
            }
            return
        }

        _isLoading.value = true
        _error.value = null
        _isTodayLearningSessionComplete.value = false
        currentWordIndex = 0
        currentCycle = 0
        wordsForCurrentSession = emptyList()

        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting '오늘의 학습' session. User: $userId, Goal: $dailyGoalFromActivity")

                // Repository를 통한 데이터 fetch
                // Repository를 통해 우선순위 단어 + 신규 단어 조합하여 가져옴
                // ViewModel은 비즈니스 로직에만 집중
                val fetchedWords = wordRepository.getWordsForTodayLearning(userId, dailyGoalFromActivity)
                wordsForCurrentSession = fetchedWords

                if (wordsForCurrentSession.isNotEmpty()) {
                    Log.i(TAG, "Fetched ${wordsForCurrentSession.size} words for today's learning.")
                    updateCurrentWordDisplay() // 첫 번째 단어 표시
                } else {
                    // 빈 결과 처리 -> 신규 사용자이거나 모든 단어 학습 완료
                    _error.value = "오늘 학습할 단어가 없습니다."
                    _currentWordDisplayInfo.value = null
                    _isTodayLearningSessionComplete.value = true
                    Log.w(TAG, "No words available for learning.")
                }
            } catch (e: Exception) {
                // 에러처리
                // Firebase 예외, 네트워크 오류 등
                Log.e(TAG, "Error starting today's learning session", e)
                _error.value = "단어 로딩 중 오류: ${e.message}"
                _isTodayLearningSessionComplete.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun processNextWordOrRepetition() {
        // 상태 검증
        if (_isLoading.value == true || _isTodayLearningSessionComplete.value == true || wordsForCurrentSession.isEmpty()) {
            return
        }

        currentWordIndex++

        // 한 사이클 완료 (모든 단어를 한 번씩 봤음) -> 사이클 관리 로직
        if (currentWordIndex >= wordsForCurrentSession.size) {
            currentCycle++
            currentWordIndex = 0

            // 3사이클 모두 완료 -> 세션 완료 조건
            if (currentCycle >= totalCycles) {
                Log.i(TAG, "'오늘의 학습' 세션 3사이클 모두 완료.")
                _isTodayLearningSessionComplete.value = true
                prepareForTenMinReview() // Firebase 저장
                return
            }
        }

        updateCurrentWordDisplay()
    }

    private fun updateCurrentWordDisplay() {
        if (currentWordIndex < wordsForCurrentSession.size && currentWordIndex >= 0) {
            val word = wordsForCurrentSession[currentWordIndex]

            // 진행률 계산 -> 총 단어수 = 단어수 * 3사이클
            // 완료된 단어수 = (현재 사이클 * 단어수) + 현재 인덱스
            val totalWords = wordsForCurrentSession.size * totalCycles
            val completedWords = currentCycle * wordsForCurrentSession.size + currentWordIndex
            val overallProgress = "${completedWords + 1}/$totalWords"

            _currentWordDisplayInfo.value = WordDisplayInfo(
                word = word,
                currentCycle = currentCycle + 1, // 1, 2, 3으로 표시
                totalCycles = totalCycles,
                currentWordIndex = currentWordIndex,
                totalWordsInCycle = wordsForCurrentSession.size,
                overallProgress = overallProgress
            )

            // docId 포함하여 Firebase 추적 가능
            Log.d(TAG, "Displaying: ${word.word_text} (ID: ${word.docId}), " +
                    "Cycle: ${currentCycle + 1}/$totalCycles, " +
                    "Word: ${currentWordIndex + 1}/${wordsForCurrentSession.size}")
        } else {
            Log.d(TAG, "No current word to display or index out of bounds.")
        }
    }

    // Firebase 저장 로직
    private fun prepareForTenMinReview() {
        viewModelScope.launch {
            _isLoading.value = true

            // docId 추출, mapNotNuLL로 null docId 필터링(방어적 프로그래밍)
            val learnedDocIds = wordsForCurrentSession.mapNotNull { it.docId }

            if (learnedDocIds.isNotEmpty()) {
                Log.d(TAG, "Firebase에 10분 후 복습용 단어 ${learnedDocIds.size}개 저장 중...")

                // Repository를 통한 Firebase 저장
                // users/{userId}/tenMinReviewWords 컬렉션에 저장
                // 트랜잭션으로 원자성 보장 (Repository에서 처리)
                val success = wordRepository.setWordsForTenMinReview(userId, learnedDocIds)
                if (success) {
                    _learnedWordDocIdsForQuiz.value = learnedDocIds
                    Log.i(TAG, "✅ Firebase에 ${learnedDocIds.size}개 단어를 10분 후 복습용으로 저장 완료!")
                    Log.d(TAG, "저장된 단어 IDs: $learnedDocIds")
                } else {
                    // 저장 실패 처리
                    _error.value = "10분 후 복습 단어 저장에 실패했습니다."
                    Log.e(TAG, "❌ Firebase에 10분 후 복습 단어 저장 실패!")
                }
            } else {
                // 빈 세션 처리
                _learnedWordDocIdsForQuiz.value = emptyList()
                Log.i(TAG, "학습한 단어가 없어 10분 후 복습 준비를 생략합니다.")
            }
            _isLoading.value = false
        }
    }

    // UI에서 버튼 텍스트 결정용 헬퍼 함수
    // ViewModel이 UI 텍스트를 결정 (MVVM 패턴)
    fun getNextButtonText(): String {
        return if (wordsForCurrentSession.isNotEmpty() && currentWordIndex < wordsForCurrentSession.size) {
            val cycle = currentCycle + 1
            val wordProgress = "${currentWordIndex + 1}/${wordsForCurrentSession.size}"
            "넘기기"
                    // "($cycle/$totalCycles 사이클 - $wordProgress)"
        } else {
            "넘기기"
        }
    }

    fun clearError() {
        _error.value = null
    }
}