package com.example.englishapp.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
// import androidx.activity.result.ActivityResultLauncher // 현재 흐름에서는 직접 사용 안 함
// import androidx.activity.result.contract.ActivityResultContracts // 현재 흐름에서는 직접 사용 안 함
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.englishapp.R
import com.example.englishapp.data.repository.WordRepositoryImpl
import com.example.englishapp.viewmodel.LearnViewModel
import com.example.englishapp.viewmodel.LearnViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import java.net.URLEncoder
import java.util.Locale

class WordStudyActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var buttonClose: ImageButton
    private lateinit var textEnglishWord: TextView
    private lateinit var textKoreanMeaning: TextView
    private lateinit var buttonNext: Button
    private lateinit var iconDictionary: ImageView
    private lateinit var iconSpeaker: ImageView

    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false


    // Firebase Auth에서 userId 가져오기
    // 모든 API 호출시 이 userId 필요
    // - "ERROR_USER_ID"는 테스트/디버깅용 fallback
    private val userId: String by lazy {
        FirebaseAuth.getInstance().currentUser?.uid ?: "ERROR_USER_ID"
    }

    // Repository 패턴 적용
    // WordRepositoryImpl이 실제 Firebase와 통신, ViewModel이 Repository를 통해 데이터 접근
    // 추후 REST API로 교체시 Repository만 수정
    private val viewModel: LearnViewModel by viewModels {
        LearnViewModelFactory(userId, WordRepositoryImpl())
    }

    // private lateinit var postLearningQuizLauncher: ActivityResultLauncher<Intent> // 현재 흐름에서는 직접 사용 안 함

    companion object {
        //  Activity 간 데이터 전달용 키
        // - DAILY_GOAL_EXTRA: 서버에서 가져온 일일 목표
        // - RESULT_LEARNED_WORDS_COUNT: 학습 완료된 단어 수 (통계용)
        const val DAILY_GOAL_EXTRA = "daily_goal"
        const val RESULT_LEARNED_WORDS_COUNT = "learned_words_count" // 결과 전달용 키
        private const val TAG = "WordStudyActivity" // 로깅용
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //  인증 실패 처리
        // - userId가 없으면 모든 Firebase 작업 불가
        // - RESULT_CANCELED로 실패 상태 전달

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study)

        if (userId == "ERROR_USER_ID") {
            Toast.makeText(this, "로그인 정보가 없습니다. 앱을 다시 시작해주세요.", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        initializeUI()
        // initializeLaunchers() // 현재 흐름에서는 이 Activity 내에서 다른 Activity를 직접 실행하지 않음

        // 단어 읽어주기 TTS
        tts = TextToSpeech(this, this)


        // 일일 목표 검증
        // 0 이하면 잘못된 데이터(서버 오류 또는 전달 오류)
        val dailyGoal = intent.getIntExtra(DAILY_GOAL_EXTRA, 0)
        if (dailyGoal <= 0) {
            Toast.makeText(this, "학습 목표 단어 수가 유효하지 않습니다.", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setupClickListeners()
        setupObservers()

        // 화면 회전시 중복 호출 방지
        // 이미 진행중인 세션은 ViewModel 유지
        if (savedInstanceState == null) {
            viewModel.startTodayLearningSession(dailyGoal)
        }
    }

    private fun initializeUI() {
        buttonClose = findViewById(R.id.button_close)
        textEnglishWord = findViewById(R.id.text_english_word)
        textKoreanMeaning = findViewById(R.id.text_korean_meaning)
        buttonNext = findViewById(R.id.button_next)
        iconDictionary = findViewById(R.id.icon_dictionary)
        iconSpeaker = findViewById(R.id.icon_speaker)
    }

    /* // 현재 흐름에서는 직접 사용 안 함
    private fun initializeLaunchers() {
        postLearningQuizLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(TAG, "10-min WordQuizActivity finished with result code: ${result.resultCode}")
            setResult(result.resultCode)
            finish()
        }
    }
    */

    // 학습 상황
    private fun setupClickListeners() {
        buttonClose.setOnClickListener {
            setResult(Activity.RESULT_CANCELED) // 사용자가 중간에 닫음
            finish()
        }

        buttonNext.setOnClickListener {
            if (viewModel.isLoading.value == false) {
                viewModel.processNextWordOrRepetition()
            }
        }

        iconDictionary.setOnClickListener { openDictionary() }
        iconSpeaker.setOnClickListener { speakCurrentWord() }
    }

    // 로딩 상태 관리
    // Firebase 작업 중 UI 비활성화, 중복 요청 방지
    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            buttonNext.isEnabled = !isLoading
            iconSpeaker.isEnabled = !isLoading && isTtsInitialized
            iconDictionary.isEnabled = !isLoading

            if (isLoading) {
                textEnglishWord.text = "로딩 중..."
                textKoreanMeaning.text = ""
            }
        }

        // 단어 표시 정보 관찰
        // WordDisplayInfo는 UI 전용 데이터 클래스
        // 서버 데이터(Word)를 UI용으로 가공, null 체크로 다양한 상태 처리
        viewModel.currentWordDisplayInfo.observe(this) { displayInfo ->
            if (displayInfo != null) {
                textEnglishWord.text = displayInfo.word.word_text
                textKoreanMeaning.text = displayInfo.word.word_mean
                buttonNext.text = viewModel.getNextButtonText() // ViewModel에서 버튼 텍스트 가져오기

                // 디버깅용 상세 로그
                Log.d(TAG, "현재 단어: ${displayInfo.word.word_text}, " +
                        "사이클: ${displayInfo.currentCycle}/${displayInfo.totalCycles}, " +
                        "전체 진행률: ${displayInfo.overallProgress}")
            } else if (viewModel.isLoading.value == false && viewModel.isTodayLearningSessionComplete.value == false) {
                // 로딩 중이 아니고, 세션 완료도 아닌데 displayInfo가 null인 경우 (예: 단어 로딩 실패 후 초기 상태)
                textEnglishWord.text = ""
                textKoreanMeaning.text = ""
                buttonNext.text = "시작하기" // 또는 적절한 초기 텍스트
            }
        }

        // 세선 완료 처리
        viewModel.isTodayLearningSessionComplete.observe(this) { isComplete ->
            if (isComplete == true) { // 세션이 완료된 경우 (성공/실패/단어없음 모두 포함)
                val resultIntent = Intent()
                if (viewModel.error.value == null) { // 오류 없이 완료
                    //  비동기 처리 패턴
                    // - prepareForTenMinReview()가 Firebase에 저장 완료 후
                    // - learnedWordDocIdsForQuiz LiveData 업데이트
                    // - 그 후에 결과 반환 (observe로 대기)
                    viewModel.learnedWordDocIdsForQuiz.observe(this) { learnedIds ->
                        // 이 블록은 learnedWordDocIdsForQuiz가 실제로 업데이트될 때 호출됨
                        val learnedCount = learnedIds?.size ?: 0
                        resultIntent.putExtra(RESULT_LEARNED_WORDS_COUNT, learnedCount)
                        setResult(Activity.RESULT_OK, resultIntent)

                        // 로깅 : 학습 완료 통계
                        Log.i(TAG, "'오늘의 학습' 정상 완료. 학습 단어 수: $learnedCount. 결과 설정 후 Activity 종료.")
                        if (!isFinishing) finish()
                    }
                    // 만약 learnedWordDocIdsForQuiz가 즉시 업데이트되지 않을 경우를 대비해,
                    // 또는 wordsForCurrentSession이 ViewModel에 public으로 노출되어 있다면 그것을 사용할 수도 있음.
                    // 현재는 prepareForTenMinReview가 완료되면 learnedWordDocIdsForQuiz가 업데이트된다고 가정.
                } else { // 오류시 통계를 0으로 처리
                    Log.e(TAG, "Learning session completed with error: ${viewModel.error.value}")
                    resultIntent.putExtra(RESULT_LEARNED_WORDS_COUNT, 0)
                    setResult(Activity.RESULT_CANCELED, resultIntent) // 오류 시 RESULT_CANCELED
                    if (!isFinishing) finish()
                }
            }
        }

        // 에러 처리
        // Firebase 오류, 네트워크 오류 등, clearError()로 일회성 표시 보장
        viewModel.error.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, "오류: $it", Toast.LENGTH_LONG).show()
                viewModel.clearError() // 오류 메시지 표시 후 ViewModel에서 클리어
                // 오류 발생 시 Activity를 어떻게 처리할지 결정 (예: 즉시 종료)
                // setResult(Activity.RESULT_CANCELED)
                // finish()
            }
        }
    }

    // TTS 기능(영어 읽어주기)
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.ENGLISH)
            isTtsInitialized = !(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
            iconSpeaker.isEnabled = isTtsInitialized && (viewModel.isLoading.value == false)

            if (!isTtsInitialized) {
                Log.e(TAG, "TTS: Language not supported or missing data.")
                // Toast.makeText(this, "영어 TTS가 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "TTS initialized successfully.")
            }
        } else {
            iconSpeaker.isEnabled = false
            Log.e(TAG, "TTS initialization failed with status: $status")
            // Toast.makeText(this, "TTS 초기화 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // 뷰모델에서 값을 받아와 사용
    private fun speakCurrentWord() {
        if (isTtsInitialized && viewModel.isLoading.value == false) {
            val wordText = viewModel.currentWordDisplayInfo.value?.word?.word_text
            if (!wordText.isNullOrEmpty()) {
                tts.speak(wordText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        } else {
            if (!isTtsInitialized) Toast.makeText(this, "음성 기능 사용 불가", Toast.LENGTH_SHORT).show()
        }
    }

    // 외부 API 연동(네이버 사전)
    // URLEncoder로 한글/특수문자 처리
    // 네트워크 오류시 예외 처리
    private fun openDictionary() {
        if (viewModel.isLoading.value == false) {
            val wordText = viewModel.currentWordDisplayInfo.value?.word?.word_text
            if (!wordText.isNullOrEmpty()) {
                try {
                    val encodedWord = URLEncoder.encode(wordText, "UTF-8")
                    // URL(API 사용) -> query에 단어를 학습화면에 뜬 단어를 사용해서 출력
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://en.dict.naver.com/#/search?query=$encodedWord"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "사전 열기 실패", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error opening dictionary for word: $wordText", e)
                }
            }
        }
    }

    // TTS 종료
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}