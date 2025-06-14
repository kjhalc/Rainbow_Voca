package com.example.englishapp.manager

import com.example.englishapp.model.Word

class StudyManager(
    private val priorityWords: List<Word>,
    private val normalWords: List<Word>,
    private val dailyGoal: Int
) {
    private val studyList: List<Word>
    private var currentWordIndex = 0
    private var currentCycle = 0 // 현재 사이클 (0, 1, 2 총 3사이클)
    private val totalCycles = 3

    init {
        // 우선순위 단어 먼저, 부족하면 일반 단어로 채우기
        val combined = mutableListOf<Word>()
        combined.addAll(priorityWords)

        val remaining = dailyGoal - priorityWords.size
        if (remaining > 0) {
            combined.addAll(normalWords.take(remaining))
        }

        studyList = combined.take(dailyGoal)
    }

    fun getStudyList() = studyList
    fun getCurrentWord() = studyList.getOrNull(currentWordIndex)
    fun getCurrentCycle() = currentCycle + 1 // 1, 2, 3으로 표시
    fun getTotalWords() = studyList.size
    fun getCurrentWordIndex() = currentWordIndex
    fun getTotalCycles() = totalCycles

    // 넘기기 버튼 클릭 시
    fun moveToNext(): Boolean {
        currentWordIndex++

        // 한 사이클 완료 (모든 단어를 한 번씩 봤음)
        if (currentWordIndex >= studyList.size) {
            currentCycle++
            currentWordIndex = 0

            // 3사이클 모두 완료
            if (currentCycle >= totalCycles) {
                return false // 학습 종료
            }
        }

        return true // 아직 학습할 것이 남음
    }

    fun hasNext(): Boolean {
        return currentCycle < totalCycles
    }

    fun isStudyComplete(): Boolean {
        return currentCycle >= totalCycles
    }

    fun reset() {
        currentWordIndex = 0
        currentCycle = 0
    }

    // 전체 진행률 계산
    fun getOverallProgress(): String {
        val totalWords = studyList.size * totalCycles
        val completedWords = currentCycle * studyList.size + currentWordIndex
        return "$completedWords/$totalWords"
    }
}