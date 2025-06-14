package com.example.englishapp.model // 패키지 선언은 프로젝트 구조에 맞게 확인하세요

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    var nickname: String = "",
    var email: String = "", // 이메일 필드 추가 + password는 추가 X, Firebase 시스템에서 관리 해줄 것
    var dailyWordGoal: Int = 10, // 기본값 10개 (Firestore 필드명과 일치하게 사용)
    var hasStudiedToday: Boolean = false, // 기본값 false
    val createdAt: Timestamp? = null // 계정 생성 시간, Timestamp 타입 권장
)