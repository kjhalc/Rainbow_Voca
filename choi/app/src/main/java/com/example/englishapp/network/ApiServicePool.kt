package com.example.englishapp.network

import kotlin.getValue
import kotlin.jvm.java

// 모든 API 서비스를 한 곳에서 관리하는 객체
object ApiServicePool {
    // User 관련
    val userApi: UserApiService by lazy {
        StudyRoomRetrofitInstance.retrofit.create(UserApiService::class.java)
    }

    // 전체 단어 조회 & 오늘 단어 설정
    // 전체 단어 조회 -> 보안 이슈로 라이브 데이터 바로 받아서 사용
    val wordApi: WordApiService by lazy {
        StudyRoomRetrofitInstance.retrofit.create(WordApiService::class.java)
    }

    // 메인 페이지
    val mainPageApi: MainPageApiService by lazy {
        StudyRoomRetrofitInstance.retrofit.create(MainPageApiService::class.java)
    }

    // 오늘의 학습
    val learnApi: LearnApiService by lazy {
        StudyRoomRetrofitInstance.retrofit.create(LearnApiService::class.java)
    }

    // 10분 후 복습 + 누적 복습
    val reviewApi: ReviewApiService by lazy {
        StudyRoomRetrofitInstance.retrofit.create(ReviewApiService::class.java)
    }

    // 스터디방
    val studyRoomApi: StudyRoomApiService by lazy {
        StudyRoomRetrofitInstance.retrofit.create(StudyRoomApiService::class.java)
    }

    // AI 독해
    val aiReadingApi: AiReadingApiService by lazy {
        StudyRoomRetrofitInstance.retrofit.create(AiReadingApiService::class.java)
    }
}
