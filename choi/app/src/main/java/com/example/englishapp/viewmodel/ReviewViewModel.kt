package com.example.englishapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishapp.model.Word
import com.example.englishapp.network.ApiServicePool
import kotlinx.coroutines.launch

// 클라이언트가 외부 API와 상호작용하여 "학습 후 복습"에 필요한 단어 목록을 가져오는 로직
// 별도의 API 서버가 존재하는 경우 "이미그레이션"에 용이


class ReviewViewModel : ViewModel() {

    // 외부 API로부터 받아온 복습 대상 단어 목록을 LiveData로 관리
    // 이 데이터는 UI(Activity)에 표시될 원천 데이터
    private val _postLearningWords = MutableLiveData<List<Word>>()
    val postLearningWords: LiveData<List<Word>> get() = _postLearningWords

    // API 통신중 발생할 수 있는 오류 메시지를 UI에 전달하기 위한 LiveData
    // 서버 응답 실패, 네트워크 오류등을 클라이언트에 알리는 역할
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    // 외부 API를 호출하여 "10분 후 복습" 단어 목록을 비동기적으로 가져옴
    // @param token 사용자 인증을 위한 JWT 또는 세션 토큰, API 요청 시 HTTP 헤더에 포함
    fun fetchPostLearningWords(token: String) {
        // viewModelScope를 사용하여 코루틴 실행, ViewModel 생명주기와 연동
        // 서버 API 호출은 비동기 작업이므로 코루틴 사용이 적합
        viewModelScope.launch {
            try {
                // ApiServicePool을 통해 실제 API 서비스(Retrofit 등)의 구현체를 가져옴
                // "Bearer $token" 형태로 인증 토큰을 API 요청 헤더에 추가
                val response = ApiServicePool.reviewApi.getPostLearningReviewWords("Bearer $token")

                // API 응답(WordListResponse)에서 실제 단어 목록(words)을 추출하여 LiveData에 설정
// 서버 응답이 null이거나 words 필드가 null일 경우 빈 리스트로 처리 (방어적 프로그래밍)
                _postLearningWords.value = response.words ?: emptyList()
            } catch (e: Exception) {
                // API 호출 중 예외 발생시 (네트워크 오류, 서버 내부 오류 등)
                _postLearningWords.value = emptyList() // UI에는
                _errorMessage.value = "단어 목록을 불러오지 못했습니다."
            }
        }
    }
}
