package com.example.englishapp.data.repository

import com.example.englishapp.model.StudyMemberProfile
import com.example.englishapp.model.StudyRoom
import com.example.englishapp.network.ApiStudyRoomSearchResultItem
import com.example.englishapp.network.BatchNotificationRequest
import com.example.englishapp.network.CreateStudyRoomRequest
import com.example.englishapp.network.IndividualNotificationRequest
import com.example.englishapp.network.JoinStudyRoomRequest
import com.example.englishapp.network.MyStudyRoomBasicInfo
import com.example.englishapp.network.StudyRoomApiService
import com.example.englishapp.network.StudyRoomDetailsResponse
import com.example.englishapp.utils.ApiResult
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.englishapp.model.StageDistribution
import com.google.firebase.firestore.AggregateSource

class StudyRoomRepository(
    private val remoteDataSource: StudyRoomApiService,
) {
    private val db = Firebase.firestore

    private suspend fun getAuthHeader(): String? {
        return try {
            val token = Firebase.auth.currentUser?.getIdToken(true)?.await()?.token
            token?.let { "Bearer $it" }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun <T> safeApiCall(apiCall: suspend (String) -> T): ApiResult<T> {
        val authHeader = getAuthHeader()
            ?: return ApiResult.Error("인증 토큰을 가져올 수 없습니다. 로그인이 필요합니다.")

        return withContext(Dispatchers.IO) {
            try {
                // 토큰 유효성 확인용 로그
                android.util.Log.d("StudyRoom", "Making API call with token: ${authHeader.take(20)}...")

                val result = apiCall(authHeader)
                ApiResult.Success(result)
            } catch (e: retrofit2.HttpException) {
                // HTTP 에러 상세 처리
                val errorBody = e.response()?.errorBody()?.string()
                val errorCode = e.code()

                android.util.Log.e("StudyRoom", "HTTP Error $errorCode: $errorBody")

                // 에러 코드별 메시지
                val errorMessage = when (errorCode) {
                    400 -> "잘못된 요청입니다. ($errorBody)"
                    401 -> "인증이 만료되었습니다. 다시 로그인해주세요."
                    403 -> "접근 권한이 없습니다."
                    404 -> "요청한 리소스를 찾을 수 없습니다."
                    else -> "서버 오류가 발생했습니다. (코드: $errorCode)"
                }

                ApiResult.Error(errorMessage)
            } catch (e: Exception) {
                android.util.Log.e("StudyRoom", "General Exception: ${e.message}", e)
                ApiResult.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    // 방만들기
    suspend fun createStudyRoom(title: String, password: String): ApiResult<StudyRoom> {
        return safeApiCall { authHeader ->
            val request = CreateStudyRoomRequest(title, password)
            val response = remoteDataSource.createStudyRoom(authHeader, request)
            if (response.success == true) {
                StudyRoom(
                    title = response.title ?: title,
                    password = password,
                    ownerNickname = response.ownerNickname ?: "",
                    ownerId = response.ownerId ?: 0,
                    members = mutableListOf(),
                    isAdminForCurrentUser = true,
                    memberCount = 1
                )
            } else {
                throw Exception(response.message ?: "방 생성에 실패했습니다.")
            }
        }
    }

    // 방 들어가기
    suspend fun joinStudyRoom(title: String, password: String): ApiResult<StudyRoom> {
        return safeApiCall { authHeader ->
            val request = JoinStudyRoomRequest(title, password)

            // 디버깅용 로그 추가
            android.util.Log.d("StudyRoom", "Join request - title: $title, hasPassword: ${password.isNotEmpty()}")

            val response = remoteDataSource.joinStudyRoom(authHeader, request)

            if (response.success == true && response.roomDetails != null) {
                // currentUserId는 서버 응답이나 토큰에서 추출
                val currentUid = Firebase.auth.currentUser?.uid ?: ""
                val currentUserId = convertUidToNumericId(currentUid)

                mapStudyRoomDetailsResponseToStudyRoom(response.roomDetails, password, currentUserId)
            } else {
                throw Exception(response.message ?: "방 참여에 실패했습니다.")
            }
        }
    }

    suspend fun getMyJoinedStudyRooms(): ApiResult<List<MyStudyRoomBasicInfo>> {
        return safeApiCall { authHeader ->
            remoteDataSource.getMyStudyRooms(authHeader)
        }
    }

    suspend fun getStudyRoomDetails(roomTitle: String, roomPassword: String, currentUserId: Int): ApiResult<StudyRoom> {
        return safeApiCall { authHeader ->
            val response = remoteDataSource.getStudyRoomDetails(authHeader, roomTitle)
            mapStudyRoomDetailsResponseToStudyRoom(response, roomPassword, currentUserId)
        }
    }

    // 강퇴하기
    suspend fun kickStudyRoomMemberById(roomTitle: String, memberUserIdToKick: Int): ApiResult<Unit> {
        return safeApiCall { authHeader ->
            val response = remoteDataSource.deleteStudyRoomMember(authHeader, roomTitle, memberUserIdToKick)
            if (response.success != true) { throw Exception(response.message ?: "멤버 강퇴에 실패했습니다.") }
            Unit
        }
    }
    // 방 나가기
    suspend fun leaveStudyRoom(title: String): ApiResult<Unit> {
        return safeApiCall { authHeader ->
            val response = remoteDataSource.leaveStudyRoom(authHeader, mapOf("title" to title))
            if (response.success != true) { throw Exception(response.message ?: "방 나가기에 실패했습니다.") }
            Unit
        }
    }

    suspend fun searchRooms(query: String): ApiResult<List<ApiStudyRoomSearchResultItem>> {
        return safeApiCall { authHeader ->
            if (query.isBlank()) return@safeApiCall emptyList()
            remoteDataSource.searchStudyRooms(authHeader, query)
        }
    }

    // 개별 알림
    suspend fun sendIndividualNotification(roomTitle: String, targetUserId: Int): ApiResult<Unit> {
        return safeApiCall { authHeader ->
            val request = IndividualNotificationRequest(roomTitle, targetUserId)
            val response = remoteDataSource.sendIndividualNotification(authHeader, request)
            if (response.success != true) { throw Exception(response.message ?: "알림 발송에 실패했습니다.") }
            Unit
        }
    }

    // 일괄 알림
    suspend fun sendBatchNotification(roomTitle: String, targetUserIds: List<Int>): ApiResult<Unit> {
        return safeApiCall { authHeader ->
            val request = BatchNotificationRequest(roomTitle, targetUserIds)
            val response = remoteDataSource.sendBatchNotification(authHeader, request)
            if (response.success != true) { throw Exception(response.message ?: "일괄 알림 발송에 실패했습니다.") }
            Unit
        }
    }

    private fun mapStudyRoomDetailsResponseToStudyRoom(dto: StudyRoomDetailsResponse, passwordForModel: String, currentUserId: Int): StudyRoom {
        val members = dto.members.map { apiMember ->
            StudyMemberProfile(
                userId = apiMember.userId,
                nickname = apiMember.nickname,
                profileImage = apiMember.profileImage,
                isAttendedToday = apiMember.dailyStatus?.isStudiedToday ?: false,
                //totalWordCount = apiMember.progress.totalWordCount,
                //studiedWordCount = apiMember.progress.studiedWordCount,
                wrongAnswerCount = apiMember.progress.redStageWordCount
            )
        }
        return StudyRoom(
            title = dto.title,
            password = passwordForModel,
            ownerNickname = dto.ownerNickname,
            ownerId = dto.ownerId, // Int 타입으로 수정
            members = members.toMutableList(),
            isAdminForCurrentUser = (dto.ownerId == currentUserId),
            memberCount = members.size
        )
    }



    fun listenToRoomDetailsRealtime(
        roomTitle: String,
        currentUserId: Int,
    ): Flow<ApiResult<StudyRoom>> = callbackFlow {
        var roomRegistration: ListenerRegistration? = null
        var membersRegistration: ListenerRegistration? = null

        try {
            val roomQuery = db.collection("studyRooms").whereEqualTo("title", roomTitle).limit(1)

            roomRegistration = roomQuery.addSnapshotListener { roomSnapshot, error ->
                if (error != null) {
                    trySend(ApiResult.Error(error.message ?: "방 정보 조회 중 오류가 발생했습니다."))
                    return@addSnapshotListener
                }
                if (roomSnapshot == null || roomSnapshot.isEmpty) {
                    trySend(ApiResult.Error("방을 찾을 수 없습니다."))
                    return@addSnapshotListener
                }

                val roomDoc = roomSnapshot.documents.first()
                val roomData = roomDoc.toObject(StudyRoom::class.java)!!
                val ownerUidString = roomDoc.getString("ownerId") ?: ""
                roomData.ownerId = convertUidToNumericId(ownerUidString) // 수동 할당

                membersRegistration?.remove()
                membersRegistration = db.collection("studyRoomMembers").document(roomDoc.id).collection("members")
                    .addSnapshotListener { membersSnapshot, membersError ->
                        if (membersError != null) {
                            trySend(ApiResult.Error(membersError.message ?: "멤버 정보 조회 중 오류가 발생했습니다."))
                            return@addSnapshotListener
                        }
                        if (membersSnapshot == null) return@addSnapshotListener

                        val memberProfiles = membersSnapshot.documents.mapNotNull { doc ->
                            val dailyProgressMap = doc.get("dailyProgress") as? Map<String, Any> ?: emptyMap()
                            val stageDistMap = dailyProgressMap["stageDistribution"] as? Map<String, Any> ?: emptyMap()
                            StudyMemberProfile(
                                userId = convertUidToNumericId(doc.id),
                                nickname = doc.getString("nickname") ?: "",
                                profileImage = doc.getString("profileImage"),
                                isAttendedToday = dailyProgressMap["hasStudiedToday"] as? Boolean ?: false,
                                wrongAnswerCount = (dailyProgressMap["todayWrongCount"] as? Long)?.toInt() ?: 0,
                                stageDistribution = StageDistribution(
                                    stage0 = (stageDistMap["stage0"] as? Long)?.toInt() ?: StageDistribution.TOTAL_WORDS,
                                    stage1 = (stageDistMap["stage1"] as? Long)?.toInt() ?: 0,
                                    stage2 = (stageDistMap["stage2"] as? Long)?.toInt() ?: 0,
                                    stage3 = (stageDistMap["stage3"] as? Long)?.toInt() ?: 0,
                                    stage4 = (stageDistMap["stage4"] as? Long)?.toInt() ?: 0,
                                    stage5 = (stageDistMap["stage5"] as? Long)?.toInt() ?: 0,
                                    stage6 = (stageDistMap["stage6"] as? Long)?.toInt() ?: 0
                                )
                            )
                        }

                        trySend(ApiResult.Success(roomData.copy(
                            members = memberProfiles.toMutableList(),
                            memberCount = memberProfiles.size,
                            isAdminForCurrentUser = roomData.ownerId == currentUserId
                        )))
                    }
            }
        } catch (e: Exception) {
            trySend(ApiResult.Error(e.message ?: "알 수 없는 오류가 발생했습니다."))
        }

        awaitClose {
            roomRegistration?.remove()
            membersRegistration?.remove()
        }
    }


    private fun convertUidToNumericId(uid: String): Int {
        if (uid.isBlank()) return 0
        val hash = uid.substring(0, minOf(8, uid.length)).sumOf { it.code }
        return hash % 100000
    }
}