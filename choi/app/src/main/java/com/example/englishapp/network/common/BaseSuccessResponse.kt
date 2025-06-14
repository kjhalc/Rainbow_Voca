package com.example.englishapp.network.common

// 복습 결과 전송 응답
data class BaseSuccessResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val correctCount: Int? = null,
    val incorrectCount: Int? = null,
    val error: String? = null
)
