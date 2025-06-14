package com.example.englishapp.network

import com.example.englishapp.model.Word
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

// 오늘의 학습 목표 설정 요청
data class LearningGoalRequest(
    val dailyGoal: Int
)

// 오늘의 학습 목표 설정 응답
data class LearningGoalResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val updatedGoal: Int? = null,
    val error: String? = null
)


// 전체 단어
data class GetEntireWordResponse(
    val totalCount: Int? = null,
    val totalPages: Int? = null,
    val currentPage: Int? = null,
    val words: List<Word>? = null,
    val error: String? = null,
    val message: String? = null
)

interface WordApiService {
    // 오늘의 학습 목표 설정
    @PUT("/api/user/settings/learning-goal")
    suspend fun setLearningGoal(
        @Body request: LearningGoalRequest
    ): LearningGoalResponse

    // 전체 단어 조회 -> 이건 그냥 한번에 불러오는걸로 사용
    // 보안 이슈때문에 함부로 못건듦..
    @GET("/api/words")
    suspend fun getEntireWords(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): GetEntireWordResponse
}
