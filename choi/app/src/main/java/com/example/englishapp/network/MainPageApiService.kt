// --- [최종 추천 코드] network/MainPageApiService.kt ---
package com.example.englishapp.network

import retrofit2.http.GET
import retrofit2.http.Header

// 백엔드 응답(MainPageResponse)에 포함될 데이터 구조들
data class StageCounts(
    val red: Int, val orange: Int, val yellow: Int, val green: Int,
    val blue: Int, val indigo: Int, val violet: Int
)

// [수정] 성공했을 때의 데이터만 명확하게 정의
// error와 message 필드는 ApiResult.Error 클래스가 담당하므로 제거합니다.
data class MainPageResponse(
    val stageCounts: StageCounts,
    val todayReviewGoal: Int,
    val todayLearningGoal: Int,
    val estimatedCompletionDate: String,
    val monthlyAttendanceRate: Double,
    val isTodayLearningComplete: Boolean,
    val isPostLearningReviewReady: Boolean
)

// API 호출 명세
interface MainPageApiService {
    // [수정] 실제 백엔드에 구현한 Cloud Function 이름으로 엔드포인트를 명확히 합니다.
    // 임시 테스트용 URL 대신 최종 URL을 사용합니다.
    @GET("getMainPageInfo")
    suspend fun getMainPageInfo(@Header("Authorization") token: String): MainPageResponse
}