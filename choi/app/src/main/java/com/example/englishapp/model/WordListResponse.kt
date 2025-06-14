package com.example.englishapp.model

data class WordListResponse(
    val words: List<Word>? = null,
    val error: String? = null,
    val message: String? = null
)
