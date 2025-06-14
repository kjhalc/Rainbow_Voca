package com.example.englishapp.network

import com.example.englishapp.model.WordListResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Body


// 복습 결과 단어별 정보
data class ReviewResultItem(
    val wordId: Int,
    val isCorrect: Boolean
)

// 복습 결과 전송 요청
data class StagedReviewResultRequest(
    val sessionId: String?,
    val results: List<ReviewResultItem>
)

// 복습 결과 전송 응답
data class BaseSuccessResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val correctCount: Int? = null,
    val incorrectCount: Int? = null,
    val error: String? = null
)

interface ReviewApiService {
    // 오늘의 복습 단어 목록 조회
    @GET("/api/review/staged/today-words")
    suspend fun getTodayReviewWords(
        @Header("Authorization") token: String
    ): WordListResponse

    // 10분 후 복습 단어 목록 조회
    @GET("/api/review/post-learning-words")
    suspend fun getPostLearningReviewWords(
        @Header("Authorization") token: String
    ): WordListResponse

    // 오늘의 복습 결과 전송
    @POST("/api/review/staged/results")
    suspend fun sendReviewResults(
        @Header("Authorization") token: String,
        @Body request: StagedReviewResultRequest
    ): BaseSuccessResponse

    // 10분 후 복습 결과 전송
    @POST("/api/review/post-learning/results")
    suspend fun sendPostLearningResults(
        @Header("Authorization") token: String,
        @Body request: StagedReviewResultRequest
    ): BaseSuccessResponse
}