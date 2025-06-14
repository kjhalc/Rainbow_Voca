package com.example.englishapp.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header

// 데이터 클래스 (Request/Response)
data class SignUpRequest(
    val nickname: String
)

data class UserInfo(
    val userId: Int,
    val nickname: String
)

data class SignUpResponse(
    val success: Boolean?,
    val user: UserInfo?,
    val token: String?,
    val error: String?,
    val message: String?
)

data class LoginRequest(
    val nickname: String
)

data class LoginResponse(
    val success: Boolean?,
    val user: UserInfo?,
    val token: String?,
    val error: String?,
    val message: String?
)

data class AutoLoginResponse(
    val user: UserInfo?,
    val error: String?,
    val message: String?
)

interface UserApiService {
    // 회원가입
    @POST("/api/users/signup")
    suspend fun signUp(
        @Body request: SignUpRequest
    ): SignUpResponse

    // 로그인
    @POST("/api/users/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    // 자동로그인 (오토로그인)
    @GET("/api/users/me")
    suspend fun autoLogin(
        @Header("Authorization") token: String
    ): AutoLoginResponse
}