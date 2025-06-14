// --- [5. ViewModel 수정] viewmodel/MainPageViewModel.kt ---
package com.example.englishapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.englishapp.data.repository.MainRepository
import com.example.englishapp.model.MainPageData
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.example.englishapp.data.repository.WordRepositoryImpl

enum class LearningButtonState { LOADING, READY_TO_START_LEARNING, TEN_MIN_REVIEW_AVAILABLE, LEARNING_COMPLETED }

class MainPageViewModel(
    application: Application,
    val userId: String,
    private val mainRepository: MainRepository
) : AndroidViewModel(application) {

    private val wordRepository = WordRepositoryImpl()

    private val _mainPageData = MutableLiveData<MainPageData>()
    val mainPageData: LiveData<MainPageData> = _mainPageData

    private val _learningButtonState = MutableLiveData<LearningButtonState>(LearningButtonState.LOADING)
    val learningButtonState: LiveData<LearningButtonState> = _learningButtonState

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var mainPageListener: ListenerRegistration? = null

    init {
        if (userId != "UNKNOWN_USER") {
            startListeningToMainPageData()
        } else {
            _errorMessage.value = "로그인 정보가 없습니다."
        }
    }

    fun updateDailyGoal(newGoal: Int) {
        // ViewModel의 코루틴 스코프를 사용하여 비동기 작업을 안전하게 처리
        viewModelScope.launch {
            val success = wordRepository.updateUserDailyWordGoalInFirestore(userId, newGoal)
            if (!success) {
                _errorMessage.value = "학습 목표 업데이트에 실패했습니다."
            }

        }
    }


    private fun startListeningToMainPageData() {
        // 리스너 중복 부착 방지
        if (mainPageListener != null) return

        mainPageListener = mainRepository.observeMainPageData(userId,
            onUpdate = { data ->
                // 데이터가 업데이트될 때마다 LiveData를 갱신
                _mainPageData.value = data

                // 받은 데이터를 기반으로 버튼 상태 결정
                val newState = when {
                    // 🔥 순서 중요: 학습 완료 체크를 먼저
                    data.isTodayLearningComplete && !data.isPostLearningReviewReady ->
                        LearningButtonState.LEARNING_COMPLETED

                    data.isTodayLearningComplete && data.isPostLearningReviewReady ->
                        LearningButtonState.TEN_MIN_REVIEW_AVAILABLE

                    !data.isTodayLearningComplete ->
                        LearningButtonState.READY_TO_START_LEARNING

                    else -> LearningButtonState.LEARNING_COMPLETED
                }
                _learningButtonState.value = newState
            },
            onError = { exception ->
                _errorMessage.value = "실시간 데이터 로딩 오류: ${exception.message}"
            }
        )
    }

    // ViewModel이 파괴될 때 리스너를 반드시 제거하여 메모리 누수를 방지합니다.
    override fun onCleared() {
        super.onCleared()
        mainPageListener?.remove()
    }
}