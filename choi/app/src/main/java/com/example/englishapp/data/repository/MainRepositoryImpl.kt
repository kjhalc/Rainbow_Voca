// --- [최종 완성본 2] data/repository/MainRepositoryImpl.kt ---
package com.example.englishapp.data.repository

import android.util.Log
import com.example.englishapp.model.MainPageData
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainRepositoryImpl : MainRepository {
    private val db = Firebase.firestore
    private val TAG = "MainRepositoryImpl"

    override fun observeMainPageData(
        userId: String,
        onUpdate: (MainPageData) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        // 1. Firestore에서 특정 사용자 문서를 가리킵니다.
        val userDocRef = db.collection("users").document(userId)

        Log.d(TAG, "Attaching snapshot listener to users/$userId")

        // 2. 이 함수가 바로 실시간 마법의 핵심입니다.
        //    한번 호출되면, 서버의 해당 문서에 변경이 있을 때마다
        //    중괄호 `{}` 안의 코드가 '자동으로 계속해서' 실행됩니다.
        return userDocRef.addSnapshotListener { snapshot, error ->
            // 에러 처리
            if (error != null) {
                Log.e(TAG, "Listen failed for user $userId", error)
                onError(error)
                return@addSnapshotListener
            }

            // 스냅샷(최신 데이터) 처리
            if (snapshot != null && snapshot.exists()) {
                // 3. Firestore로부터 받은 데이터를 우리가 정의한 MainPageData Kotlin 객체로 변환합니다.
                val mainPageData = snapshot.toObject(MainPageData::class.java)
                if (mainPageData != null) {
                    Log.d(TAG, "Data updated for user $userId: ${mainPageData.todayReviewCount} review words")
                    // 4. 성공적으로 데이터를 받아 객체로 변환하면, ViewModel에게
                    //    "최신 데이터 여기 있어!"라고 알려주는 onUpdate 콜백 함수를 호출합니다.
                    onUpdate(mainPageData)
                } else {
                    onError(Exception("데이터 변환에 실패했습니다."))
                }
            } else {
                onError(Exception("사용자 문서를 찾을 수 없습니다."))
            }
        }
    }
}