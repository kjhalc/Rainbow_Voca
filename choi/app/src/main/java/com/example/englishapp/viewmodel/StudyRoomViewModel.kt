// 파일 위치: viewmodel/StudyRoomViewModel.kt (기존 파일 덮어쓰기)

package com.example.englishapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.englishapp.data.repository.StudyRoomRepository
import com.example.englishapp.data.repository.UserRepository
import com.example.englishapp.model.StudyMemberProfile
import com.example.englishapp.model.StudyRoom
import com.example.englishapp.network.ApiStudyRoomSearchResultItem
import com.example.englishapp.network.MyStudyRoomBasicInfo
import com.example.englishapp.utils.ApiResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest


class StudyRoomViewModel(
    application: Application,
    private val studyRoomRepository: StudyRoomRepository, // 밖에서 주입받음
    private val userRepository: UserRepository             // 밖에서 주입받음
) : AndroidViewModel(application) {

    // LiveData 정의
    private val _joinedRooms = MutableLiveData<ApiResult<List<MyStudyRoomBasicInfo>>>()
    val joinedRooms: LiveData<ApiResult<List<MyStudyRoomBasicInfo>>> get() = _joinedRooms

    private val _foundRooms = MutableLiveData<ApiResult<List<ApiStudyRoomSearchResultItem>>>()
    val foundRooms: LiveData<ApiResult<List<ApiStudyRoomSearchResultItem>>> get() = _foundRooms

    private val _createOp = MutableLiveData<ApiResult<StudyRoom>>()
    val createOp: LiveData<ApiResult<StudyRoom>> get() = _createOp

    private val _joinOp = MutableLiveData<ApiResult<StudyRoom>>()
    val joinOp: LiveData<ApiResult<StudyRoom>> get() = _joinOp

    private val _leaveOp = MutableLiveData<ApiResult<Unit>>()
    val leaveOp: LiveData<ApiResult<Unit>> get() = _leaveOp

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> get() = _message

    // 랭킹용 TOP3 변수
    private val _top3 = MutableLiveData<List<StudyMemberProfile>>()
    val top3: LiveData<List<StudyMemberProfile>> = _top3

    // 방 전체 정보
    private val _currentRoom = MutableLiveData<StudyRoom>()
    val currentRoom: LiveData<StudyRoom> = _currentRoom




    init {
        loadJoinedRooms() // ViewModel이 생성될 때 내 방 목록을 자동으로 불러옵니다.
    }
    private fun convertUidToNumericId(uid: String): Int {
        val slice = if (uid.length >= 8) uid.substring(0, 8) else uid
        val sum   = slice.fold(0) { acc, c -> acc + c.code }
        return sum % 100_000          // 0~99,999
    }

    fun startListeningToRoom(roomTitle: String) {
        val uid = userRepository.getCurrentUserId() ?: return
        val myNumericId = convertUidToNumericId(uid)

        viewModelScope.launch {
            studyRoomRepository
                .listenToRoomDetailsRealtime(roomTitle, myNumericId)
                .collectLatest { result ->
                    if (result is ApiResult.Success) {
                        val room = result.data
                        _currentRoom.postValue(room)          // (선택) 화면에 방 정보 사용

                        /* ---------- 랭킹 Top3 계산 ---------- */
                        _top3.postValue(
                            room.members
                                .sortedByDescending { it.progressRate }     // 진도율 내림차순
                                .take(3)                                    // 상위 3명
                                .onEachIndexed { i, member -> member.rankOrder = i + 1 }
                        )
                    } else if (result is ApiResult.Error) {
                        _message.postValue(result.message)
                    }
                }
        }
    }

    fun loadJoinedRooms() {
        if (userRepository.getCurrentUserId() == null) {
            _message.value = "로그인이 필요합니다."
            return
        }
        safeLaunch(_joinedRooms, studyRoomRepository::getMyJoinedStudyRooms)
    }

    fun createNewRoom(title: String, password: String) {
        safeLaunch(_createOp, {
            studyRoomRepository.createStudyRoom(title, password)
        }) { room ->
            _message.value = "'${room.title}' 방 생성 완료!"
            loadJoinedRooms() // 성공 시 목록을 새로고침합니다.
        }
    }

    fun joinExistingRoom(title: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _joinOp.value = ApiResult.Loading

            // userProfile 체크 제거 - 서버가 토큰으로 자동 식별
            val result = studyRoomRepository.joinStudyRoom(title, password) // currentUserId 파라미터 제거
            _joinOp.value = result

            if (result is ApiResult.Success) {
                _message.value = "'${result.data.title}' 방 참여 완료!"
                loadJoinedRooms()
            } else if (result is ApiResult.Error) {
                // 에러 메시지를 더 친근하게 변경
                _message.value = when {
                    result.message.contains("400") -> "비밀번호가 틀렸거나 이미 참여한 방입니다."
                    result.message.contains("404") -> "존재하지 않는 방입니다."
                    result.message.contains("409") -> "이미 참여 중인 방입니다."
                    result.message.contains("403") -> "방 참여 권한이 없습니다."
                    else -> result.message
                }
            }
            _isLoading.value = false
        }
    }

    fun leaveCurrentRoom(roomTitle: String) {
        safeLaunch(_leaveOp, {
            studyRoomRepository.leaveStudyRoom(roomTitle)
        }) {
            _message.value = "'$roomTitle' 방에서 나갔습니다."
            loadJoinedRooms()
        }
    }

    fun findRooms(query: String) {
        if (query.isBlank()) {
            _foundRooms.value = ApiResult.Success(emptyList())
            return
        }
        safeLaunch(_foundRooms, { studyRoomRepository.searchRooms(query) })
    }

    fun clearMessage() { _message.value = null }
    fun clearCreateOp() { _createOp.value = ApiResult.Idle }
    fun clearJoinOp() { _joinOp.value = ApiResult.Idle }
    fun clearLeaveOp() { _leaveOp.value = ApiResult.Idle }
    fun clearFoundRooms() { _foundRooms.value = ApiResult.Idle }

    // API 호출 시 반복되는 상용구 코드를 처리하는 헬퍼 함수
    private fun <T> safeLaunch(
        liveData: MutableLiveData<ApiResult<T>>,
        apiCall: suspend () -> ApiResult<T>,
        onSuccess: ((T) -> Unit)? = null // 성공 시 추가로 실행할 동작
    ) {
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
                    onSuccess?.invoke(result.data)
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