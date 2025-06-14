// 파일 위치: app/src/main/java/com/example/englishapp/network/StudyRoomModels.kt (새로 만들기)

package com.example.englishapp.network

/**
 * 서버와 통신할 때 사용하는 데이터 모델(DTO)들을 모아두는 파일입니다.
 * DTO: Data Transfer Object
 */

// --- 요청(Request) 데이터 클래스 ---

/** 스터디룸 생성 요청 (사용자 입력: 방 제목, 비밀번호) */
data class CreateStudyRoomRequest(
    val title: String,
    val password: String
)

/** 스터디룸 참여 요청 (사용자 입력: 방 제목, 비밀번호) */
data class JoinStudyRoomRequest(
    val title: String,
    val password: String
)

/** 개별 학생 독촉 알림 요청 */
data class IndividualNotificationRequest(
    val roomTitle: String,
    val targetUserId: Int
)

/** 다수 학생 독촉 알림 일괄 요청 */
data class BatchNotificationRequest(
    val roomTitle: String,
    val targetUserIds: List<Int>
)


// --- 응답(Response) 데이터 클래스 ---

/** 스터디룸 생성 응답 (생성된 방 정보 포함) */
data class CreateStudyRoomResponse(
    val title: String,
    val ownerNickname: String,
    val ownerId: Int,
    val message: String? = null,
    val success: Boolean? = null
)

/** 스터디룸 참여 응답 (참여 성공 시 방 상세 정보 포함) */
data class JoinStudyRoomResponse(
    val success: Boolean?,
    val message: String?,
    val roomDetails: StudyRoomDetailsResponse?
)

/** 내가 참여한 스터디룸 목록의 각 아이템 정보 */
data class MyStudyRoomBasicInfo(
    val title: String,
    val ownerNickname: String,
    val ownerId: Int,
    val memberCount: Int
)

/** 스터디룸 상세 정보 (API DTO) */
data class StudyRoomDetailsResponse(
    val title: String,
    val ownerNickname: String,
    val ownerId: Int,
    val isAdmin: Boolean, // 현재 요청 사용자가 이 방의 관리자인지 여부
    val members: List<StudyRoomMember>
)

/** 스터디룸 멤버 정보 (API DTO) */
data class StudyRoomMember(
    val userId: Int,
    val nickname: String,
    val role: String, // "OWNER" 또는 "MEMBER"
    val profileImage: String?,
    val progress: StudyRoomProgress,
    val dailyStatus: StudyRoomDailyStatus? = null
)

/** 스터디룸 멤버의 학습 진행도 정보 */
data class StudyRoomProgress(
    val totalWordCount: Int,
    val studiedWordCount: Int, // 추가
    val redStageWordCount: Int
)

/** 스터디룸 멤버의 일일 학습 상태 */
data class StudyRoomDailyStatus(
    val isStudiedToday: Boolean
)

/** 스터디룸 검색 결과 아이템 DTO (서버 응답용) */
data class ApiStudyRoomSearchResultItem(
    val title: String,
    val ownerNickname: String,
    val memberCount: Int,
    val isLocked: Boolean
)