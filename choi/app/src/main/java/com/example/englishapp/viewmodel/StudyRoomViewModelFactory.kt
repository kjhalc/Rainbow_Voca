// 파일 위치: viewmodel/StudyRoomViewModelFactory.kt (새로 만들기)

package com.example.englishapp.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.englishapp.data.repository.StudyRoomRepository
import com.example.englishapp.data.repository.UserRepository
import com.example.englishapp.network.StudyRoomRetrofitInstance
import com.example.englishapp.network.StudyRoomApiService

/**
 * StudyRoomViewModel에 필요한 의존성(Repository)을 주입하기 위한 팩토리 클래스
 */
class StudyRoomViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyRoomViewModel::class.java)) {
            // ViewModel이 필요로 하는 전문가(Repository)들을 여기서 생성합니다.
            val studyRoomRepository = StudyRoomRepository(
                StudyRoomRetrofitInstance.retrofit.create(StudyRoomApiService::class.java)
            )
            val userRepository = UserRepository()

            // 생성한 전문가들을 ViewModel 생성자에 인자로 전달하여 주입합니다.
            @Suppress("UNCHECKED_CAST")
            return StudyRoomViewModel(application, studyRoomRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}