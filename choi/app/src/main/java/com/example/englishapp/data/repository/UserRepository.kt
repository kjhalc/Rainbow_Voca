// 파일 위치: data/repository/UserRepository.kt (새로 만들기)

package com.example.englishapp.data.repository

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * 사용자 정보(인증, 프로필 등)와 관련된 모든 데이터 처리를 책임지는 클래스
 */
class UserRepository {

    /**
     * 현재 로그인된 사용자의 고유 ID(UID)를 반환합니다.
     */
    fun getCurrentUserId(): String? = Firebase.auth.currentUser?.uid

    /**
     * Firestore DB에서 현재 사용자의 프로필 정보를 가져옵니다.
     * @return UserProfile 객체. 실패 시 null 반환.
     */
    suspend fun getUserProfile(): UserProfile? {
        val uid = getCurrentUserId() ?: return null
        return try {
            val document = Firebase.firestore.collection("users").document(uid).get().await()
            if (document.exists()) {
                UserProfile(
                    uid = uid,
                    numericId = convertUidToNumericId(uid),
                    nickname = document.getString("nickname") ?: "Unknown"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Firebase UID를 앱에서 사용할 숫자 ID로 변환하는 내부 함수
     */
    private fun convertUidToNumericId(uid: String): Int {
        // JS의 로직과 동일하게, 8자리 문자열의 각 문자 코드 값을 더하는 방식으로 변경
        val hash = uid.substring(0, 8).sumOf { it.code }
        return hash % 100000
    }
}

/**
 * ViewModel 등에서 사용할 사용자 프로필 정보를 담는 간단한 데이터 클래스
 */
data class UserProfile(
    val uid: String,
    val numericId: Int,
    val nickname: String
)