package com.example.englishapp.model
// 스터디룸 용
data class DailyProgress(
    val hasStudiedToday: Boolean = false,
    val progressRate: Int = 0,              // 0-100 퍼센트
    val todayWrongCount: Int = 0,
    val lastUpdated: Long? = null,
    val stageDistribution: StageDistribution = StageDistribution()
)