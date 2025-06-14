package com.example.englishapp.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class QuizQuestion(
    val word: Word,
    val options: List<String>, // 4개 요소 (1정답 + 3오답)
    val correctIndex: Int // 정답이 위치한 인덱스 저장
) : Parcelable