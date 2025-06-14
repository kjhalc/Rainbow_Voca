
// ========== RetrofitInstance.kt 수정 ==========
package com.example.englishapp.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object StudyRoomRetrofitInstance {

    // Firebase Functions URL로 변경
    // 실제 배포 URL 또는 에뮬레이터 URL 사용
    private const val BASE_URL = "https://asia-northeast3-englishapp-1fc84.cloudfunctions.net/studyRoomApi/"

    // 테스트용(로컬 에뮬러이터)
    // private const val BASE_URL = "http://10.0.2.2:5001/englishapp-1fc84/asia-northeast3/studyRoomApi/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
