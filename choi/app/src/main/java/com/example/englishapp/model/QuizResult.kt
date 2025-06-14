package com.example.englishapp.model // 실제 프로젝트 경로로 수정

data class QuizResult(
    val wordId: Int, // Word 객체의 숫자 ID
    val isCorrect: Boolean
)