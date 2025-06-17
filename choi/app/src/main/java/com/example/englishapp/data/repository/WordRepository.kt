package com.example.englishapp.data.repository

import com.example.englishapp.model.IndividualWordState
import com.example.englishapp.model.ReviewWord
import com.example.englishapp.model.UserProfile
import com.example.englishapp.model.Word
import java.util.Date

interface WordRepository {

    // ---- 사용자 프로필 관련 ----
    suspend fun getUserProfile(userId: String): UserProfile?
    suspend fun updateUserStudiedTodayFlag(userId: String, hasStudied: Boolean): Boolean

    // ---- 학습 단어 가져오기 관련 ----
    suspend fun getWordsForTodayLearning(userId: String, dailyGoal: Int): List<Word>
    suspend fun setWordsForTenMinReview(userId: String, wordDocIds: List<String>): Boolean

    // ---- 10분 후 복습 관련 ----
    suspend fun getWordsForTenMinReview(userId: String): List<Word>
    suspend fun moveTenMinReviewWordToNextStage(userId: String, wordDocId: String): Boolean
    suspend fun moveTenMinReviewWordToIndividualStateOnIncorrect(userId: String, wordDocId: String): Boolean
    suspend fun clearPendingTenMinReviewWords(userId: String): Boolean

    // --- 누적 복습 관련 ---
    suspend fun getWordsForCumulativeReview(userId: String): List<Word>
    suspend fun updateCumulativeReviewWordOnCorrect(userId: String, wordDocId: String, currentReviewWord: ReviewWord): Boolean
    suspend fun moveCumulativeReviewWordToIndividualStateOnIncorrect(userId: String, wordDocId: String): Boolean


    // --- 퀴즈 및 그래프 관련 ----
    suspend fun getRandomWordMeanings(count: Int, excludeMeanings: List<String>): List<String> // 추가
    suspend fun getTotalWordCountInSource(): Int
    suspend fun getReviewWordCountsByStageMap(userId: String): Map<Int, Int>
    suspend fun getCurrentlyReviewingWordsCount(userId: String): Int
    suspend fun getReviewWordsCountByStage(userId: String, stage: Int, isMastered: Boolean): Int

    // --- Helper 및 기타 --
    // -- 단어 문서 ID로 단어 정보 조회
    suspend fun getWordByDocId(wordDocId: String): Word?  // -- 복습 단어 정보 조회 --
    suspend fun getReviewWord(userId: String, wordDocId: String): ReviewWord? // -- 개별 단어 상태 조회 --
    suspend fun getIndividualWordState(userId: String, wordDocId: String): IndividualWordState?
    suspend fun updateUserDailyWordGoalInFirestore(userId: String, newGoal: Int): Boolean // -- 오늘의 학습 단어 개수 설정 --



    // --- 메인 화면 핵심 기능 ---
    // 실시간 사용자 프로필(닉네임, 학습 목표 등)의 변경을 감지
    // fun observeUserProfile(userId: String, callback: (UserProfile?) -> Unit)
    // 오늘 복습해야할 단어의 개수 변경으 감지
    // fun observeTodayReviewCount(userId: String, callback: (Int) -> Unit)

    // [추가] 아래 두 개의 함수 선언을 추가해주세요.
    // fun observeReviewStageCounts(userId: String, callback: (Map<Int, Int>) -> Unit)
    fun observeTenMinReviewCount(userId: String, callback: (Int) -> Unit)



    // 예상 완료일 계산
    // 사용자의 모든 단어 상태, 일일 학습 목표, 평균 정답률을 기반으로 학습 완료 예상 날짜 계산
//    suspend fun calculateDetailedCompletionDate(
//        userId: String,
//        dailyGoal: Int,
//        averageAccuracy: Float = 0.8f // 평균 정답률 80% 가정
//    ): String

    // 이달의 성실도 계산
    // 이번 달 학습 시작일로부터 어제까지의 기간 중 빠진 날 하루당 3%씩 차감
    // 이거 일단 인터페이스 및 구현체에 만들어 놓긴했는데 실질적으로 사용 X -> 진도율로 바꿈
    suspend fun getMonthlyDiligence(userId: String, today: Date): Int


    // 학습일 기록
    // 하루 학습을 완료 했을 때, 오늘 날짜를 학습 기록에 저장 -> 추후 성실도와 예상 완료일 계산에 용이
    suspend fun recordStudyDay(userId: String, date: Date): Boolean


    // 뷰모델이 파괴될 때 모든 리스너를 정리하여 메모리 누수 방지
    fun removeAllListeners(userId: String)

    // --- AI 독해용 맞은 단어 저장 기능 ---
    fun saveCorrectWordsForToday(userId: String, words: List<String>, onComplete: (Boolean) -> Unit)



}
