package com.example.englishapp.utils

sealed class ApiResult<out T> {
    object Idle : ApiResult<Nothing>() // 초기 또는 작업 없는 상태
    object Loading : ApiResult<Nothing>()
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}