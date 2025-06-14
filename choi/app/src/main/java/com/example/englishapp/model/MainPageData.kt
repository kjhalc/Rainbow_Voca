package com.example.englishapp.model

import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MainPageData(
    // UserProfile에서 가져올 정보
    val nickname: String = "",
    val email: String = "",
    @get:PropertyName("dailyWordGoal") @set:PropertyName("dailyWordGoal")
    var dailyWordGoal: Int = 10,

    // 백엔드가 실시간으로 계산해주는 정보
    @get:PropertyName("todayReviewCount") @set:PropertyName("todayReviewCount")
    var todayReviewCount: Int = 0,
    @get:PropertyName("estimatedCompletionDate") @set:PropertyName("estimatedCompletionDate")
    var estimatedCompletionDate: String = Companion.calculateSimpleInitialDate(), // 수정된 부분
    @get:PropertyName("progressRate") @set:PropertyName("progressRate")
    var progressRate: Double = 0.0,
    @get:PropertyName("stageCounts") @set:PropertyName("stageCounts")
    var stageCounts: Map<String, Int> = emptyMap(),

    // 버튼 상태를 결정할 플래그들
    @get:PropertyName("isTodayLearningComplete") @set:PropertyName("isTodayLearningComplete")
    var isTodayLearningComplete: Boolean = false,
    @get:PropertyName("isPostLearningReviewReady") @set:PropertyName("isPostLearningReviewReady")
    var isPostLearningReviewReady: Boolean = false
) {
    // Firestore toObject()를 위한 빈 생성자
    constructor() : this(
        "",
        "",
        10,
        0,
        Companion.calculateSimpleInitialDate(), // 수정된 부분
        0.0,
        emptyMap(),
        false,
        false
    )

    // 초기 날짜 계산을 위한 로직을 companion object에 추가
    companion object {
        private fun calculateSimpleInitialDate(): String {
            val totalWords = 200
            val initialDailyGoal = 10
            // 하루에 10개씩 200개를 학습하는 데 걸리는 날짜 계산 (올림)
            val days = (totalWords + initialDailyGoal - 1) / initialDailyGoal

            // 오늘 날짜에 위에서 계산한 날짜를 더합니다.
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, days)

            // "yyyy년 MM월 dd일" 형식으로 변환합니다.
            val sdf = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN)
            return sdf.format(calendar.time)
        }
    }
}