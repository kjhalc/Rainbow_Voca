package com.example.englishapp.viewmodel

// ======================================================================
// 🚫 사용하지 않는 ViewModel - WordQuizViewModel로 대체됨
// ======================================================================
// 이 ViewModel은 구버전 QuizManager 방식을 사용했으나,
// 현재는 WordQuizViewModel + WordRepository 방식으로 변경
// ======================================================================

// 아키텍쳐 마이그레이션의 흔적
// QuizManager (로컬 메모리 기반) -> 신버젼 : WordRepository (Firebase 기반)


/* 주석 처리된 코드 분석:
//  백엔드 관점: QuizManager는 로컬에서만 동작하는 방식
// - 서버와의 동기화 없음
// - 오프라인 전용 설계
// - 세션 데이터가 메모리에만 존재 (앱 종료시 소실)

//  ReviewResultItem 생성 부분
// - wordId 기반으로 결과 생성
// - isCorrect는 attempts >= 1로 단순 판단
// - 백엔드로 전송할 데이터 구조 (현재는 미사용)
*/




/*
// quiz activity랑 연동 10분 후 복습

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.englishapp.manager.QuizManager
import com.example.englishapp.manager.QuizWordRepository
import com.example.englishapp.model.QuizQuestion
import com.example.englishapp.model.Word
import com.example.englishapp.network.ReviewResultItem

class StudyViewModel : ViewModel() {
    private lateinit var quizManager: QuizManager
    private val _currentQuestion = MutableLiveData<QuizQuestion?>()
    val currentQuestion: LiveData<QuizQuestion?> = _currentQuestion

    private val _progress = MutableLiveData<Pair<Int, Int>>()
    val progress: LiveData<Pair<Int, Int>> = _progress

    fun startQuiz(words: List<Word>) {
        quizManager = QuizManager(words)
        updateProgress()
        generateNextQuestion()
    }

    fun nextQuestion(isCorrect: Boolean) {
        quizManager.handleAnswer(isCorrect)
        generateNextQuestion()
        updateProgress()
    }

    private fun generateNextQuestion() {
        _currentQuestion.value = quizManager.generateQuestion()
    }

    private fun updateProgress() {
        _progress.value = quizManager.getCompletedCount() to quizManager.getTotalCount()
    }

     // ✅ QuizWordRepository에서 단어 리스트를 가져와 결과 생성
    fun getReviewResults(): List<ReviewResultItem> {
        return QuizWordRepository.quizWords.map { word ->
            ReviewResultItem(
                wordId = word.id,
                isCorrect = quizManager.getCurrentAttempts(word.id) >= 1
            )
        }
    }
}
*/