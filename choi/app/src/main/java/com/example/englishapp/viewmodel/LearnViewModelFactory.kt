package com.example.englishapp.viewmodel // 실제 프로젝트 경로로 수정

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.englishapp.data.repository.WordRepository

// 의존성 주입(DI)을 위한 팩토리
// userId와 Repository를 ViewModel에 주입


class LearnViewModelFactory(
    private val userId: String, // Firebase Auth UID
    private val repository: WordRepository // Repository 인터페이스 (추상화)
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LearnViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LearnViewModel(userId, repository) as T
        }
        // 타입 안정성 보장
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}