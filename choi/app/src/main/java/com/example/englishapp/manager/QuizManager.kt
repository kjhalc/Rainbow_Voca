package com.example.englishapp.manager

import android.os.Parcelable
import com.example.englishapp.model.QuizQuestion
import com.example.englishapp.model.Word
import kotlinx.android.parcel.Parcelize

@Parcelize
class QuizManager(private val quizWords: List<Word>) : Parcelable {
    private val remainingWords = quizWords.toMutableList() // 정답 처리된 단어 외의 단어
    private var lastQuestionWordId: Int? = null // 가장 최근에 문제로 나왔던 단어
    private val successCount = mutableMapOf<Int, Int>() // 단어 카운트

    // 현재 단어의 시도 횟수 가져오기
    fun getCurrentAttempts(wordId: Int): Int {
        return successCount[wordId] ?: 0
    }

    fun generateQuestion(): QuizQuestion? {
        // 1. 직전 단어 제외
        val candidates = remainingWords.filter {
            it.id != lastQuestionWordId
        }

        // 2. 정답 단어 랜덤 선택
        val correctWord = candidates.randomOrNull() ?: remainingWords.randomOrNull() ?: return null
        lastQuestionWordId = correctWord.id

        // 3. 오답 생성 (remainingWords 중 정답 제외)
        val wrongMeanings = (remainingWords - correctWord)
            .shuffled()
            .take(3)
            .map { it.word_mean }

        // 4. 임의로 뽑은 틀린 뜻과 올바른 뜻을 섞은 리스트 반환
        val allOptions = (wrongMeanings + correctWord.word_mean).shuffled()
        val correctIndex = allOptions.indexOfFirst { it == correctWord.word_mean }

        return QuizQuestion(correctWord, allOptions, correctIndex)
    }

    fun handleAnswer(isCorrect: Boolean) {
        val currentWordId = lastQuestionWordId ?: return

        if (isCorrect) {
            // 5. 정답 횟수 증가
            val count = (successCount[currentWordId] ?: 0) + 1
            successCount[currentWordId] = count
        }
    }

    // 진행률 표시용
    fun getCompletedCount() = quizWords.count { (successCount[it.id] ?: 0) >= 1 }
    fun getTotalCount() = quizWords.size
}
