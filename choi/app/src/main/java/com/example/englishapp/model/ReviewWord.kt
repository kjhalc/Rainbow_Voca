package com.example.englishapp.model // 실제 패키지 경로 확인

import com.google.firebase.Timestamp // Firestore의 날짜/시간 타입

// Firestore users/{userId}/review_words/{wordDocumentId} 경로의 문서 모델
data class ReviewWord(
    // 스키마의 `stage` (INTEGER) 필드에 해당합니다.
    // 0: "오늘의 학습" 직후 (10분 후 복습 대기)
    // 1: 1일차 복습 대상 (10분 후 복습 통과)
    // 6: 28일차 복습 대상 (이 단계 통과 시 isMastered = true)
    val stage: Int = 0,

    // 스키마의 `added_date` (DATE) 필드와 유사한 역할입니다.
    // 이 단어를 마지막으로 학습했거나, 복습 퀴즈에서 정답을 맞힌 가장 최근 시간입니다.
    val lastReviewedAt: Timestamp? = null,

    // 이 필드는 스키마에는 없지만, 복습 시스템 구현에 매우 유용합니다.
    // `stage`와 `lastReviewedAt`을 기반으로 계산된 "다음 복습 예정일"입니다.
    // 이 값을 기준으로 "오늘 복습할 단어"를 쉽게 쿼리할 수 있습니다.
    val nextReviewAt: Timestamp? = null,

    // 이 단어를 완전히 마스터했는지 여부를 나타냅니다. (최종 복습 단계 통과 시 true)
    // 스키마에는 없지만, 복습 흐름 관리에 유용하여 추가
    val isMastered: Boolean = false
) {
    // Firestore가 data class를 객체로 변환할 때 (toObject() 호출 시)
    // 내부적으로 매개변수 없는 생성자를 필요로 합니다.
    // data class는 주 생성자의 모든 매개변수에 기본값이 있으면 자동으로 생성해주지만, 명시하는 것이 안전할 수 있습니다.
    constructor() : this(0, null, null, false)
}