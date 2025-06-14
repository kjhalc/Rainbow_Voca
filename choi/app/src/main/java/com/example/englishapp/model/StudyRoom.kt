package com.example.englishapp.model

//// 스터디룸의 정보를 나타내는 데이터 클래스
//data class StudyRoom(
//    val title: String, // 스터디룸의 제목 (고유 식별자로 사용)
//    val password: String, // 스터디룸 참여 시 필요한 비밀번호
//    val ownerNickname: String, // 스터디룸을 생성한 방장의 닉네임
//    val ownerId: Int, // 스터디룸을 생성한 방장의 고유 ID
//    var members: MutableList<StudyMemberProfile> = mutableListOf(), // 스터디룸에 참여한 멤버 목록
//    var isAdminForCurrentUser: Boolean = false, // 현재 사용자가 이 방의 관리자인지 여부
//    var memberCount: Int = 0 // 스터디룸의 현재 멤버 수 (members.size로 계산 가능)
//) {
//    // 현재 멤버 수를 반환하는 함수
//    fun getCurrentMemberCount(): Int = members.size
//}
import com.google.firebase.firestore.IgnoreExtraProperties
import androidx.annotation.Keep
import com.google.firebase.firestore.Exclude


@Keep
@IgnoreExtraProperties
data class StudyRoom(
    val title: String = "",
    val password: String = "",
    val ownerNickname: String = "",
    @get:Exclude
    var ownerId: Int = 0,
    var members: MutableList<StudyMemberProfile> = mutableListOf(),
    var isAdminForCurrentUser: Boolean = false,
    var memberCount: Int = 0
)