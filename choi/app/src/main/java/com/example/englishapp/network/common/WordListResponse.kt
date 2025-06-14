package com.example.englishapp.network.common

import com.example.englishapp.model.Word

data class WordListResponse(
    val words: List<Word>? = null,
    val error: String? = null,
    val message: String? = null
)
