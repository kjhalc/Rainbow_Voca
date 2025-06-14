package com.example.englishapp.model


data class Student(
    val name: String,
    val profileResId: Int,
    val attendedToday: Boolean,
    val memorizedWords: Int,
    val totalWords: Int
)