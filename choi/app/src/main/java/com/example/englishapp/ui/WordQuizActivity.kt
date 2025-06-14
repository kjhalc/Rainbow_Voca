package com.example.englishapp.ui

import android.app.Activity
import android.content.Intent
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
import com.example.englishapp.data.repository.WordRepositoryImpl
import com.example.englishapp.viewmodel.QuizQuestionDisplay
import com.example.englishapp.viewmodel.WordQuizViewModel
import com.example.englishapp.viewmodel.WordQuizViewModelFactory
import kotlinx.coroutines.launch
import java.util.Locale

class WordQuizActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // UI 요소들
    private lateinit var textQuestion: TextView
    private lateinit var textProgress: TextView
    private lateinit var optionButtons: List<Button>
    private lateinit var buttonClose: ImageButton
    private lateinit var iconQuizSpeaker: ImageView

    // TTS 관련
    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false

    // 🔥 추가: Repository 인스턴스
    private lateinit var repository: WordRepositoryImpl
    private lateinit var userId: String

    private val viewModel: WordQuizViewModel by viewModels {
        val quizType = intent.getStringExtra(QUIZ_TYPE_EXTRA) ?: run {
            Log.e(TAG, "WordQuizActivity: QuizType is missing in Intent. Defaulting to '10min' for ViewModel factory.")
            "10min"
        }
        userId = intent.getStringExtra(USER_ID_EXTRA) ?: run {
            Log.e(TAG, "WordQuizActivity: UserId is missing in Intent. Using ERROR_USER_ID for ViewModel factory.")
            "ERROR_USER_ID"
        }
        repository = WordRepositoryImpl()
        WordQuizViewModelFactory(userId, quizType, repository)
    }

    companion object {
        const val QUIZ_TYPE_EXTRA = "quizType"
        const val USER_ID_EXTRA = "userId"
        const val RESULT_TOTAL_QUESTIONS_IN_QUIZ = "total_questions_in_quiz"
        const val RESULT_QUIZ_TYPE = "quiz_type_result"
        private const val TAG = "WordQuizActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        val receivedUserId = intent.getStringExtra(USER_ID_EXTRA)
        val receivedQuizType = intent.getStringExtra(QUIZ_TYPE_EXTRA)

        if (receivedUserId.isNullOrEmpty() || receivedUserId == "ERROR_USER_ID" || receivedQuizType.isNullOrEmpty()) {
            Toast.makeText(this, "퀴즈 실행에 필요한 정보가 부족합니다.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Finishing WordQuizActivity due to missing extras. UserId: $receivedUserId, QuizType: $receivedQuizType")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        Log.i(TAG, "onCreate: QuizType=$receivedQuizType, UserId=$receivedUserId")

        initializeUI()
        tts = TextToSpeech(this, this)

        setupClickListeners()
        setupObservers()

        if (savedInstanceState == null) {
            viewModel.loadQuiz()
        }
    }

    private fun initializeUI() {
        buttonClose = findViewById(R.id.button_close)
        textProgress = findViewById(R.id.text_quiz_progress)
        textQuestion = findViewById(R.id.text_quiz_word)
        iconQuizSpeaker = findViewById(R.id.icon_quiz_speaker)
        optionButtons = listOf(
            findViewById(R.id.button_choice1),
            findViewById(R.id.button_choice2),
            findViewById(R.id.button_choice3),
            findViewById(R.id.button_choice4)
        )
    }

    private fun setupClickListeners() {
        buttonClose.setOnClickListener {
            Log.d(TAG, "Close button clicked. Setting result to CANCELED and finishing.")
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        iconQuizSpeaker.setOnClickListener { speakCurrentWord() }

        optionButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                if (viewModel.isLoading.value == false && viewModel.showAnswerFeedback.value == null) {
                    Log.d(TAG, "Option button $index clicked.")
                    viewModel.submitAnswer(index)
                } else {
                    Log.d(TAG, "Option button click ignored. isLoading=${viewModel.isLoading.value}, feedbackShowing=${viewModel.showAnswerFeedback.value != null}")
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            val enableButtons = !isLoading && viewModel.showAnswerFeedback.value == null
            optionButtons.forEach { it.isEnabled = enableButtons }
            iconQuizSpeaker.isEnabled = enableButtons && isTtsInitialized
        }

        viewModel.currentQuestion.observe(this) { questionDisplay ->
            if (questionDisplay != null) {
                displayQuestion(questionDisplay)
            } else if (viewModel.isLoading.value == false && viewModel.isQuizFinished.value == false) {
                Log.w(TAG, "Current question is null, but quiz not loading or finished. Check for errors.")
                textQuestion.text = "문제 로딩 중..."
                optionButtons.forEach { it.text = ""; it.isEnabled = false }
            }
        }

        viewModel.quizProgressText.observe(this) { progressText ->
            textProgress.text = progressText
        }

        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }

        viewModel.showAnswerFeedback.observe(this) { feedbackPair ->
            feedbackPair?.let { (isCorrect, feedback) ->
                Toast.makeText(this, feedback, Toast.LENGTH_SHORT).show()
                optionButtons.forEach { it.isEnabled = false }
                textQuestion.postDelayed({
                    viewModel.moveToNextQuestionAfterFeedback()
                }, 1000)
            }
        }

        // 🔥 수정된 퀴즈 종료 처리
        viewModel.isQuizFinished.observe(this) { isFinished ->
            if (isFinished == true) {
                val (correctAnswers, totalQuestionsInSession) = viewModel.getQuizResults()
                val wasOverallSuccessful = viewModel.wasQuizSuccessfulOverall.value ?: false

                Log.i(TAG, "Quiz finished observer triggered. QuizType: ${intent.getStringExtra(QUIZ_TYPE_EXTRA)}, Correct: $correctAnswers/$totalQuestionsInSession, DB Operations Successful: $wasOverallSuccessful")

                // 🔥 추가: 퀴즈 완료 후 처리
                lifecycleScope.launch {
                    try {
                        val quizType = intent.getStringExtra(QUIZ_TYPE_EXTRA)

                        // 10분 후 복습 완료 시 isPostLearningReviewReady를 false로
                        if (quizType == "10min" && totalQuestionsInSession > 0) {
                            val updated = repository.completeTenMinReview(userId)
                            Log.d(TAG, "10분 후 복습 완료 처리: $updated")
                        }

                        // 3가지 모두 완료했는지 체크
                        val allCompleted = repository.checkAndUpdateDailyCompletion(userId)
                        if (allCompleted) {
                            Log.d(TAG, "✅ 오늘의 모든 학습 완료! hasStudiedToday = true")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "퀴즈 완료 후 처리 실패", e)
                    }
                }

                val resultIntent = Intent()
                resultIntent.putExtra(RESULT_TOTAL_QUESTIONS_IN_QUIZ, totalQuestionsInSession)
                resultIntent.putExtra(RESULT_QUIZ_TYPE, intent.getStringExtra(QUIZ_TYPE_EXTRA))

                setResult(if (wasOverallSuccessful) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultIntent)

                if (!isFinishing) {
                    finish()
                }
            }
        }
    }

    private fun displayQuestion(questionDisplay: QuizQuestionDisplay) {
        textQuestion.text = questionDisplay.word.word_text
        optionButtons.forEachIndexed { index, button ->
            button.text = questionDisplay.options.getOrNull(index) ?: ""
            button.isEnabled = true
        }
        iconQuizSpeaker.isEnabled = isTtsInitialized && (viewModel.isLoading.value == false)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.ENGLISH)
            isTtsInitialized = !(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
            iconQuizSpeaker.isEnabled = isTtsInitialized && (viewModel.isLoading.value == false && viewModel.currentQuestion.value != null)
            if (!isTtsInitialized) Log.e(TAG, "TTS: Language not supported or missing data.")
            else Log.i(TAG, "TTS initialized successfully.")
        } else {
            iconQuizSpeaker.isEnabled = false
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    private fun speakCurrentWord() {
        if (isTtsInitialized && viewModel.isLoading.value == false) {
            val wordText = viewModel.getCurrentWordText()
            if (!wordText.isNullOrEmpty()) {
                tts.speak(wordText, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                Log.w(TAG, "speakCurrentWord: Word text is null or empty.")
            }
        } else {
            if (!isTtsInitialized) Toast.makeText(this, "음성 엔진이 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "speakCurrentWord: TTS not ready or ViewModel loading.")
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
            Log.d(TAG, "TTS shutdown.")
        }
        super.onDestroy()
    }
}