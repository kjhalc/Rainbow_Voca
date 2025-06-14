package com.example.englishapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishapp.model.Word
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AllWordsViewModel : ViewModel() {

    private val tag = "AllWordsViewModel"
    private val firestore = FirebaseFirestore.getInstance()

    private val _wordsList = MutableLiveData<List<Word>>()
    val wordsList: LiveData<List<Word>> = _wordsList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

//    private val _statsText = MutableLiveData<String>()
//    val statsText: LiveData<String> = _statsText

    private var originalWords = listOf<Word>()

    init {
        loadAllWords()
    }

    private fun loadAllWords() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = firestore.collection("words")
                    .orderBy("id", Query.Direction.ASCENDING)
                    .get()
                    .await()

                originalWords = snapshot.toObjects(Word::class.java)
                _wordsList.value = originalWords
                // updateStats(originalWords.size, originalWords.size)

                Log.i(tag, "전체 단어 로드 완료: ${originalWords.size}개")

            } catch (e: Exception) {
                Log.e(tag, "Firestore 로드 실패", e)
                // updateStats(0, 0)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String?) {
        val listToShow = if (query.isNullOrBlank()) {
            originalWords
        } else {
            originalWords.filter { word ->
                word.word_text.contains(query, ignoreCase = true) ||
                        word.word_mean.contains(query, ignoreCase = true)
            }
        }
        _wordsList.value = listToShow
        // updateStats(listToShow.size, originalWords.size)
    }
}

//    private fun updateStats(filteredSize: Int, totalSize: Int) {
//        if (filteredSize == totalSize && totalSize > 0) {
//            _statsText.value = "전체 ${totalSize}개의 단어"
//        } else if (totalSize > 0) {
//            _statsText.value = "${filteredSize}개 검색됨 (전체 ${totalSize}개)"
//        } else {
//            _statsText.value = "표시할 단어가 없습니다."
//        }
//    }
//}