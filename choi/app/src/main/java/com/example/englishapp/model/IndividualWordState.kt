package com.example.englishapp.model

data class IndividualWordState(

    // id, word_id, user_id 필드는 firestore 경로와 문서 ID를 통해 표현

    // 우선순위 여부 필드에 해당
    // 이 점수가 높을수록 "오늘의 학습"에 먼저 노출
    val priorityScore: Int = 0
)