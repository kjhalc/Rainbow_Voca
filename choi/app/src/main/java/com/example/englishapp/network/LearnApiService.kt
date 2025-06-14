package com.example.englishapp.network

import com.example.englishapp.model.WordListResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Body



// 학습 세션 완료 요청
data class TodayCompleteRequest(
    val completedWordIds: List<Int>
)

// 학습 세션 완료 응답
data class TodayCompleteResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null
)

interface LearnApiService {
    // 오늘의 학습 단어 목록 조회
    @GET("abe280a2-268f-4c91-8087-74c2a53d3c4d")
    // /api/learn/today-words
    suspend fun getTodayWords(
        @Header("Authorization") token: String
    ): WordListResponse // 사용자 확인

    // 학습 세션 완료 보고
    @POST("/api/learn/session/complete")
    suspend fun completeTodaySession(
        @Header("Authorization") token: String,
        @Body request: TodayCompleteRequest
    ): TodayCompleteResponse
}