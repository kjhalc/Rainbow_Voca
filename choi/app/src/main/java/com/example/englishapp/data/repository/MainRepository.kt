// --- [최종 완성본 1] data/repository/MainRepository.kt ---
package com.example.englishapp.data.repository

import com.example.englishapp.model.MainPageData
import com.google.firebase.firestore.ListenerRegistration

interface MainRepository {
    fun observeMainPageData(
        userId: String,
        onUpdate: (MainPageData) -> Unit, // 데이터 업데이트 시 호출될 콜백
        onError: (Exception) -> Unit      // 에러 발생 시 호출될 콜백
    ): ListenerRegistration // 리스너를 해제할 수 있도록 등록 객체 반환
}