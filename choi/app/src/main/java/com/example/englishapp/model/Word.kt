package com.example.englishapp.model // 실제 프로젝트 경로로 수정

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName // Firestore 필드명 매핑 시 사용
import kotlinx.parcelize.Parcelize

@Parcelize
data class Word(
    @get:PropertyName("id") @set:PropertyName("id") var id: Int = 0, // Firestore 'id' 필드 (숫자형 고유 ID)
    @get:PropertyName("word_text") @set:PropertyName("word_text") var word_text: String = "", // Firestore 'word_text' 필드
    @get:PropertyName("word_mean") @set:PropertyName("word_mean") var word_mean: String = "",  // Firestore 'word_mean' 필드

    // Firestore 문서의 실제 ID (예: "word001"). Firestore에서 직접 매핑되는 필드는 아니지만,
    // 문서를 가져온 후 코드에서 이 필드에 문서 ID를 설정하여 사용합니다.
    var docId: String? = null
) : Parcelable {
    // Firestore 역직렬화를 위한 빈 생성자
    constructor() : this(0, "", "", null)
}