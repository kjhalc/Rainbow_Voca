package com.example.englishapp.viewmodel

import androidx.lifecycle.MutableLiveData
import com.example.englishapp.model.StudyRoom // StudyRoom 모델 import

/** 스터디룸 데이터 공유용 싱글톤 객체 */
object StudyRoomDataHolder {
    val allKnownRoomsLiveData = MutableLiveData<MutableMap<String, StudyRoom>>(mutableMapOf()) // 모든 방 정보 (StudyRoom 사용)
    var isDataInitialized = false // 초기 데이터 로드 확인 플래그
}