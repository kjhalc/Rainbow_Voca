package com.example.englishapp.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Header

// AI 독해 요청
data class AiMakeRequest(
   // val style: String? = null,       // 요청 스타일
    val wordIds: List<Int>? = null     // 선택적: 앱이 특정 단어 ID를 전달할 때
)

// AI 독해 응답
data class AiMakeResponse(
    val passage: String? = null,
    val error: String? = null,
    val message: String? = null
)

interface AiReadingApiService {
    // AI 독해 지문 생성
    @POST("/api/ai-reading/generate")
    suspend fun generatePassage(
        @Header("Authorization") token: String,
        @Body request: AiMakeRequest = AiMakeRequest()
    ): AiMakeResponse
}