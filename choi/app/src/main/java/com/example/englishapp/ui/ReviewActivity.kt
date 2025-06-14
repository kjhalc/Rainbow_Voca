package com.example.englishapp.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.englishapp.R
import com.example.englishapp.model.QuizQuestion
import com.example.englishapp.model.Word // Word 모델 사용
import com.example.englishapp.network.ApiServicePool
import com.example.englishapp.network.ReviewResultItem
import com.example.englishapp.network.StagedReviewResultRequest
import com.example.englishapp.viewmodel.ReviewViewModel // ReviewViewModel 사용
import kotlinx.coroutines.launch
import java.util.Locale


// 백엔드 관점: 이 Activity는 ReviewViewModel을 통해 외부 API 서버와 통신
// "학습 후 복습" 퀴즈를 진행하고, 그 결과를 다시 서버로 전송하는 역할
// WordQuizActivity와는 달리, Firebase가 아닌 별도의 백엔드 시스템과 연동

// 이미그레이션에 용이


class ReviewActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // UI 요소들
    private lateinit var textQuestion: TextView
    private lateinit var textProgress: TextView
    private lateinit var optionButtons: List<Button>
    private lateinit var buttonClose: ImageButton
    private lateinit var iconQuizSpeaker: ImageView

    // TTS 관련
    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false

    // 퀴즈 상태 관리 변수
    private var quizQuestions: List<QuizQuestion> = emptyList() // ViewModel로부터 받은 단어 목록을 반환한 퀴즈 문제 리스트
    private var currentIndex = 0 // 현재 풀고 있는 문제의 인덱스
    private val reviewResults = mutableListOf<ReviewResultItem>() // 사용자의 답변 결과를 모으는 리스트 (서버 전송용)

    // 서버 API 통신에 필요한 데이터
    private lateinit var token: String // 사용자 인증 토큰
    private var sessionId: String? = null // 복습 세션 ID

    companion object {
        private const val TAG = "ReviewActivity"
    }

    // Activity 생성 시 호출
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide() // UI 설정 -> 액션바 숨김
        setContentView(R.layout.activity_quiz) // 퀴즈 UI 레아아웃 설정

        initializeUI() // UI 요소 초기화
        tts = TextToSpeech(this, this) // TTS 초기화
        iconQuizSpeaker.isEnabled = false // TTS 초기화 전까지 스피커 버튼 비활성화

        // UI 이벤트 리스너 설정
        buttonClose.setOnClickListener { finish() } // 닫기 버튼 클릭 시 Activity 종료
        iconQuizSpeaker.setOnClickListener { speakCurrentWord() } // 스피커 버튼 클릭 시 현재 단어 TTS 재생

        // Intent로부터 인증 토큰 및 세션 ID 수신
        // 백엔드 관점: 토큰은 API 접근 권한을 확인하는 데 사용
        // 세션 ID는 특정 복습 세션을 구분하는 데 사용
        token = intent.getStringExtra("token") ?: ""
        sessionId = intent.getStringExtra("sessionId")

        // 토큰이 없으면 API 인증이 불가능하므로 즉시 종료
        if (token.isEmpty()) {
            Toast.makeText(this, "인증 토큰 없음", Toast.LENGTH_LONG).show(); finish(); return
        }

        // ReviewViewModel 인스턴스 생성 (의존성 주입)
        val viewModel: ReviewViewModel by viewModels() // ReviewViewModel 주입
        setupObservers(viewModel)
        // 백엔드 관점: ViewModel을 통해 외부 API 서버에 복습 대상 단어 목록을 요청
        viewModel.fetchPostLearningWords(token)
    }

    // UI 요소 ID 바인딩
    private fun initializeUI() {
        buttonClose = findViewById(R.id.button_close)
        textProgress = findViewById(R.id.text_quiz_progress)
        textQuestion = findViewById(R.id.text_quiz_word)
        iconQuizSpeaker = findViewById(R.id.icon_quiz_speaker)
        optionButtons = listOf(
            findViewById(R.id.button_choice1), findViewById(R.id.button_choice2),
            findViewById(R.id.button_choice3), findViewById(R.id.button_choice4)
        )
    }

    // ViewModel의 LiveData 변경 감지 및 UI 업데이트
    private fun setupObservers(viewModel: ReviewViewModel) {
        // ViewModel이 API로부터 단어 목록을 성공적으로 가져오면 이 옵저버가 호출
        viewModel.postLearningWords.observe(this) { wordList ->
            // API 응답으로 받은 단어 목록(wordList)이 비어있는 경우 처리
            if (wordList.isEmpty()) {
                if(viewModel.errorMessage.value == null) Toast.makeText(this, "복습 단어 없음", Toast.LENGTH_SHORT).show()
                finish(); return@observe
            }

            // 퀴즈 문제 생성 로직
            // 퀴즈 문제(오답 생성)를 클라이언트에서 직접 생성하는 로직
            // 서버 부하 감소, 오프라인 지원 용이
            quizQuestions = wordList.map { wordData ->
                // Word 모델에는 options 필드가 없으므로, 클라이언트에서 오답 옵션을 생성합니다.
                val correctAnswerMean = wordData.word_mean

                // 현재 단어를 제외한 나머지 단어들의 뜻 목록 (오답 후보군)
                val allOtherMeanings = wordList.filter { it.id != wordData.id }.map { it.word_mean }.distinct()

                // 오답 후보군에서 무작위로 3개 선택
                val wrongOptions = allOtherMeanings.shuffled().take(3).toMutableList()

                // 만약 다른 단어의 뜻으로 오답 3개를 채우지 못하면 더미 오답 추가
                var dummyOptionCounter = 1
                while (wrongOptions.size < 3) {
                    val dummy = "다른 뜻 ${dummyOptionCounter++}"
                    // 더미 오답이 정답과 같거나 이미 추가된 오답과 같지 않도록 방지
                    if (dummy != correctAnswerMean && !wrongOptions.contains(dummy)) {
                        wrongOptions.add(dummy)
                    }
                }

                // 최종 선택지 (오답 + 정답)를 섞고, 4개로 제한 (혹시 모를 초과 방지)
                val finalOptions = (wrongOptions + correctAnswerMean).shuffled().take(4)
                val correctIndex = finalOptions.indexOf(correctAnswerMean)

                // QuizQuestion 객체 생성
                QuizQuestion(wordData, finalOptions, if(correctIndex != -1) correctIndex else 0)
            }

            // 퀴즈 상태 초기화 및 첫 문제 표시
            currentIndex = 0; reviewResults.clear()
            showCurrentQuestion()
        }
        // ViewModel에서 API 통신 오류 발생 시 이 옵저버가 호출
        viewModel.errorMessage.observe(this) { msg -> msg?.let { Toast.makeText(this, "오류: $it", Toast.LENGTH_LONG).show() }}
    }

    // TTS 초기화 완료 시 호출되는 콜백
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.ENGLISH) // 언어 영어로 설정
            // TTS 언어 지원 여부 확인
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                iconQuizSpeaker.isEnabled = false; Log.e(TAG, "TTS: Lang not supported")
            } else {
                // TTS 사용 가능 상태로 설정 및 스피커 버튼 활성화
                isTtsInitialized = true; iconQuizSpeaker.isEnabled = true; Log.d(TAG, "TTS Init OK")
            }
        } else {
            iconQuizSpeaker.isEnabled = false; Log.e(TAG, "TTS Init Failed: $status")
        }
    }

    // 현재 문제의 단어를 TTS로 읽어주는 함수
    private fun speakCurrentWord() {
        if (isTtsInitialized) { // TTS가 초기화되었는지 확인
            val word = textQuestion.text.toString()
            if (word.isNotEmpty() && word != "로딩 중...") tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
            else Toast.makeText(this, if (word == "로딩 중...") "단어 로딩 중" else "읽을 단어 없음", Toast.LENGTH_SHORT).show()
        } else { Toast.makeText(this, "음성 기능 사용 불가", Toast.LENGTH_SHORT).show() }
    }

    // 현재 퀴즈 문제를 화면에 표시하는 함수
    private fun showCurrentQuestion() {
        // 모든 문제를 다 풀었는지 확인
        if (currentIndex >= quizQuestions.size) {
            // 기록할 결과가 있다며 서버로 전송
            if(reviewResults.isNotEmpty()) sendReviewResultsToServer()
            else { Toast.makeText(this, "퀴즈 완료 (결과 없음)", Toast.LENGTH_SHORT).show(); finish() }
            return
        }
        // 현재 문제 가져오기
        val question = quizQuestions[currentIndex]
        textQuestion.text = question.word.word_text
        // 4지선다 버튼에 선택지 텍스트 설정
        optionButtons.forEachIndexed { idx, btn ->
            btn.text = question.options.getOrNull(idx) ?: ""
            btn.isEnabled = true; btn.alpha = 1.0f
        }
        // 진행 상황 텍스트 업데이트
        textProgress.text = "${currentIndex + 1}/${quizQuestions.size}"
        iconQuizSpeaker.isEnabled = isTtsInitialized // TTS 상태에 따라 스피커 버튼 활성화/비활성화
        setupOptionClickListeners(question) // 현재 문제에 대한 선택지 버튼 클릭 리스너 설정
    }

    // 각 선택지 버튼에 대한 클릭 리스너 설정
    private fun setupOptionClickListeners(question: QuizQuestion) {
        optionButtons.forEachIndexed { idx, btn ->
            btn.setOnClickListener {
                // 답변 선택시 모든 선택지 버튼 비활성화 (중복 클릭 방지)
                optionButtons.forEach { it.isEnabled = false; it.alpha = 0.5f }
                val isCorrect = idx == question.correctIndex
                Toast.makeText(this, if (isCorrect) "정답!" else "오답!", Toast.LENGTH_SHORT).show()
                // 사용자의 답변 결과를 ReviewResultItem 형태로 로컬 리스트에 저장
                reviewResults.add(ReviewResultItem(question.word.id, isCorrect)) // question.word.id는 서버의 word_id
                currentIndex++ // 다음 문제로 인덱스 이동
                showCurrentQuestion() // 다음 문제 표시
            }
        }
    }

    // 퀴즈 결과를 서버로 전송하는 함수
    private fun sendReviewResultsToServer() {
        // 인증 토큰이나 전송할 결과가 없으면 함수를 종료
        if (token.isEmpty() || reviewResults.isEmpty()) { finish(); return }


        // StagedReviewResultRequest는 API 명세에 따른 요청 DTO(Data Transfer Object)
        // sessionId와 사용자의 모든 답변 결과(reviewResults)를 포함
        val request = StagedReviewResultRequest(sessionId, reviewResults)
        lifecycleScope.launch {
            try {
                // ApiServicePool을 통해 실제 API 서비스 호출
                // "Bearer $token" 형태로 인증 토큰 전달
                val response = ApiServicePool.reviewApi.sendPostLearningResults("Bearer $token", request)

                // 서버 응답 메시지 표시 (성공/실패 여부 등)
                Toast.makeText(this@ReviewActivity, response.message ?: (if(response.success == true) "복습 결과 전송 완료" else "복습 결과 전송 실패"), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ReviewActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                // 백엔드에서는 이때 발생한 예외(e)를 로깅하여 원인 분석 필요
            } finally {
                finish()
            }
        }
    }
    // 액티비티 소멸시 TTS 리소스 해제
    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}