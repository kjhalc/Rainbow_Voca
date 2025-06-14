// --- [4. ViewModel 수정] viewmodel/MainPageViewModelFactory.kt ---
package com.example.englishapp.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.englishapp.data.repository.MainRepositoryImpl

class MainPageViewModelFactory(
    private val application: Application,
    private val userId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainPageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainPageViewModel(application, userId, MainRepositoryImpl()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}