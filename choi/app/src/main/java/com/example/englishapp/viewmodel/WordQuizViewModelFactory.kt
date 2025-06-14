package com.example.englishapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.englishapp.data.repository.WordRepository

// WordQuizViewModel 인스턴스를 생성할 때 필요한 의존성(userId, quizType, WordRepository)을
// 생성자를 통해 주입하기 위한 팩토리 클래스
// ViewModel이 특정 사용자(userId)의 특정 퀴즈 유형(quizType)에 대한 데이터를
// 올바르게 처리하고, 적절한 데이터 저장소(WordRepository)와 상호작용하도록 초기화하는 역할
class WordQuizViewModelFactory(
    private val userId: String, // 대상 사용자 ID
    private val quizType: String, // 퀴즈의 종류 ("10min", "cumulative" 등)
    private val wordRepository: WordRepository // 데이터 액세스를 위한 리포지토리
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // 요청된 ViewModel 클래스가 WordQuizViewModel과 호환되는지 확인
        if (modelClass.isAssignableFrom(WordQuizViewModel::class.java)) {
            // 타입 캐스팅 후 의존성을 전달하여 WordQuizViewModel 인스턴스 생성 및 반환
            @Suppress("UNCHECKED_CAST")
            return WordQuizViewModel(userId, quizType, wordRepository) as T
        }
        // 요청된 클래스가 WordQuizViewModel이 아니면 예외 발생 (잘못된 사용 방지)
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}