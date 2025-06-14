// 파일 위치: app/src/main/java/com/example/englishapp/network/StudyRoomApiService.kt

package com.example.englishapp.network

import com.example.englishapp.network.common.BaseSuccessResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Query
import retrofit2.http.Header
import com.bumptech.glide.Glide
import android.widget.ImageView

/**
 * 스터디룸 관련 API 서비스 인터페이스 (API 목록)
 * 이 파일은 어떤 API를 호출할 수 있는지 '목록'만 보여주는 역할을 합니다.
 * 각 API가 사용하는 데이터 모델(Request/Response)은 StudyRoomModels.kt 파일에서 관리합니다.
 */


// 이미 RetrofitInstance 에서 BASE_URL에 studyRoomApi를 제공하기에 그걸 제외하고 사용
interface StudyRoomApiService {

    /** 스터디룸 생성 API */
    @POST("api/studyrooms")
    suspend fun createStudyRoom(
        @Header("Authorization") token: String,
        @Body request: CreateStudyRoomRequest
    ): CreateStudyRoomResponse

    /** 스터디룸 참여 API */
    @POST("api/studyrooms/join")
    suspend fun joinStudyRoom(
        @Header("Authorization") token: String,
        @Body request: JoinStudyRoomRequest
    ): JoinStudyRoomResponse

    /** 현재 사용자가 참여하고 있는 스터디룸 목록 조회 API */
    @GET("api/studyrooms/my")
    suspend fun getMyStudyRooms(
        @Header("Authorization") token: String
    ): List<MyStudyRoomBasicInfo>

    /** 특정 스터디룸의 상세 정보 조회 API */
    @GET("api/studyrooms/details")
    suspend fun getStudyRoomDetails(
        @Header("Authorization") token: String,
        @Query("title") roomTitle: String
    ): StudyRoomDetailsResponse

    /** 스터디룸에서 특정 멤버 삭제 API (방장 권한) */
    @DELETE("api/studyrooms/members")
    suspend fun deleteStudyRoomMember(
        @Header("Authorization") token: String,
        @Query("roomTitle") roomTitle: String,
        @Query("memberUserId") memberUserId: Int
    ): BaseSuccessResponse

    /** 스터디룸 나가기 API */
    @POST("api/studyrooms/leave")
    suspend fun leaveStudyRoom(
        @Header("Authorization") token: String,
        @Body roomTitleRequest: Map<String, String>
    ): BaseSuccessResponse

    /** 개별 학생에게 학습 독촉 알림 발송 API */
    @POST("api/studyrooms/notify/individual")
    suspend fun sendIndividualNotification(
        @Header("Authorization") token: String,
        @Body request: IndividualNotificationRequest
    ): BaseSuccessResponse

    /** 선택된 다수 학생에게 학습 독촉 알림 일괄 발송 API */
    @POST("api/studyrooms/notify/batch")
    suspend fun sendBatchNotification(
        @Header("Authorization") token: String,
        @Body request: BatchNotificationRequest
    ): BaseSuccessResponse

    /** 스터디룸 검색 API */
    @GET("api/studyrooms/search")
    suspend fun searchStudyRooms(
        @Header("Authorization") token: String,
        @Query("query") searchQuery: String
    ): List<ApiStudyRoomSearchResultItem>
}