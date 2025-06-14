// 파일 위치: viewmodel/AdminViewModelFactory.kt (새로 만들기)
package com.example.englishapp.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.englishapp.data.repository.StudyRoomRepository
import com.example.englishapp.data.repository.UserRepository
import com.example.englishapp.network.StudyRoomRetrofitInstance
import com.example.englishapp.network.StudyRoomApiService

class AdminViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
            // AdminViewModel이 필요로 하는 전문가들을 여기서 생성해서 전달합니다.
            val studyRoomRepository = StudyRoomRepository(
                StudyRoomRetrofitInstance.retrofit.create(StudyRoomApiService::class.java)
            )
            val userRepository = UserRepository()

            @Suppress("UNCHECKED_CAST")
            return AdminViewModel(application, studyRoomRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}