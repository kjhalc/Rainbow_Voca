// 파일 위치: viewmodel/AdminViewModel.kt
package com.example.englishapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.englishapp.data.repository.StudyRoomRepository
import com.example.englishapp.data.repository.UserRepository
import com.example.englishapp.data.repository.UserProfile
import com.example.englishapp.model.StudyRoom
import com.example.englishapp.utils.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AdminViewModel(
    application: Application,
    private val studyRoomRepository: StudyRoomRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {

    private val _roomDetails = MutableLiveData<ApiResult<StudyRoom>>()
    val roomDetails: LiveData<ApiResult<StudyRoom>> get() = _roomDetails

    private val _operationResult = MutableLiveData<ApiResult<Unit>>()
    val operationResult: LiveData<ApiResult<Unit>> get() = _operationResult

    private val _userProfile = MutableLiveData<UserProfile?>()
    val userProfile: LiveData<UserProfile?> get() = _userProfile

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> get() = _message

    // [추가] 실시간 리스너 Job을 관리할 변수
    private var roomListenerJob: Job? = null

    init {
        loadCurrentUserProfile()
    }

    private fun loadCurrentUserProfile() {
        viewModelScope.launch {
            _userProfile.value = userRepository.getUserProfile()
        }
    }

    // [수정] 일회성 로드 함수를 실시간 리스너 시작 함수로 변경
    fun startListeningToRoom(roomTitle: String) {
        // 이미 리스너가 실행 중이라면 중복 실행 방지
        if (roomListenerJob != null && roomListenerJob!!.isActive) return

        roomListenerJob = viewModelScope.launch {
            _isLoading.value = true
            val currentUser = _userProfile.value ?: run {
                _message.value = "사용자 정보를 먼저 로드해야 합니다."
                _isLoading.value = false
                return@launch
            }

            // Repository의 실시간 리스너 함수를 호출하고, 결과(Flow)를 계속 관찰(collect)
            studyRoomRepository.listenToRoomDetailsRealtime(roomTitle, currentUser.numericId) //
                .collect { result ->
                    // 첫 데이터 수신 후 로딩 상태 해제
                    if (_isLoading.value == true) {
                        _isLoading.value = false
                    }

                    // ▼▼▼▼▼▼▼▼▼▼ 이 로직을 여기에 추가해주세요 ▼▼▼▼▼▼▼▼▼▼
                    if (result is ApiResult.Success) {
                        // 1. 진도율 기준으로 멤버들을 정렬합니다.
                        val sortedMembers = result.data.members.sortedByDescending { it.progressRate }

                        // 2. 정렬된 목록에 따라 rankOrder 값을 할당합니다.
                        sortedMembers.forEachIndexed { index, member ->
                            member.rankOrder = if (index < 3) index + 1 else 0
                        }

                        // 3. 랭킹이 적용된 새 StudyRoom 객체로 LiveData를 업데이트합니다.
                        _roomDetails.value = ApiResult.Success(result.data.copy(members = sortedMembers.toMutableList()))

                    } else {
                        // 성공이 아닐 경우(Error 등)는 그대로 값을 업데이트합니다.
                        _roomDetails.value = result
                    }
                    // ▲▲▲▲▲▲▲▲▲▲ 여기까지 추가해주세요 ▲▲▲▲▲▲▲▲▲▲


                    if (result is ApiResult.Error) {
                        _message.value = result.message
                    }
                }
        }
    }

    // [수정] 강퇴 성공 시, 더 이상 수동으로 데이터를 새로고침하지 않음 (실시간 리스너가 자동 처리)
    fun kickMember(roomTitle: String, memberKickId: Int) {
        safeLaunch(_operationResult, {
            studyRoomRepository.kickStudyRoomMemberById(roomTitle, memberKickId)
        }, "멤버를 강퇴했습니다.")
    }

    fun sendStudyReminder(roomTitle: String, targetUserId: Int) {
        safeLaunch(_operationResult, {
            studyRoomRepository.sendIndividualNotification(roomTitle, targetUserId)
        }, "학습 독촉 알림을 보냈습니다.")
    }

    fun sendBatchStudyReminder(roomTitle: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val currentRoomResult = _roomDetails.value
            if (currentRoomResult !is ApiResult.Success) {
                _message.value = "방 정보가 없습니다."
                _isLoading.value = false
                return@launch
            }

            val currentUserNumericId = _userProfile.value?.numericId ?: -1
            val notStudiedUserIds = currentRoomResult.data.members
                .filter { !it.isAttendedToday && it.userId != currentUserNumericId }
                .map { it.userId }

            if (notStudiedUserIds.isEmpty()) {
                _message.value = "모든 멤버가 오늘 학습을 완료했습니다!"
                _isLoading.value = false
                return@launch
            }

            val result = studyRoomRepository.sendBatchNotification(roomTitle, notStudiedUserIds)
            if (result is ApiResult.Success) {
                _message.value = "${notStudiedUserIds.size}명에게 알림을 보냈습니다!"
            } else if (result is ApiResult.Error) {
                _message.value = "일괄 알림 발송 실패: ${result.message}"
            }
            _isLoading.value = false
        }
    }

    // [추가] ViewModel이 소멸될 때 리스너를 정리하여 메모리 누수 방지
    override fun onCleared() {
        super.onCleared()
        roomListenerJob?.cancel()
    }

    fun clearMessage() { _message.value = null }
    fun clearOperationResult() { _operationResult.value = ApiResult.Idle }

    // safeLaunch 함수는 편의상 그대로 유지합니다.
    private fun <T> safeLaunch(liveData: MutableLiveData<ApiResult<T>>, apiCall: suspend () -> ApiResult<T>, onSuccessMessage: String? = null, onSuccessAction: ((T) -> Unit)? = null) {
        viewModelScope.launch {
            if (userRepository.getCurrentUserId() == null) {
                liveData.value = ApiResult.Error("로그인이 필요합니다.")
                return@launch
            }
            _isLoading.value = true
            liveData.value = ApiResult.Loading
            when (val result = apiCall()) {
                is ApiResult.Success -> {
                    liveData.value = result
                    onSuccessMessage?.let { _message.value = it }
                    onSuccessAction?.invoke(result.data)
                }
                is ApiResult.Error -> {
                    liveData.value = result
                    _message.value = result.message
                }
                else -> { /* No-op */ }
            }
            _isLoading.value = false
        }
    }
}