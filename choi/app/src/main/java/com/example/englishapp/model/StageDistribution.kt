package com.example.englishapp.model


data class StageDistribution(
    val stage0: Int = 0,
    val stage1: Int = 0,
    val stage2: Int = 0,
    val stage3: Int = 0,
    val stage4: Int = 0,
    val stage5: Int = 0,
    val stage6: Int = 0,
) {
    companion object {
        // 하드코딩 200개 -> 일단 200개 넣어놨으니 이렇게
        const val TOTAL_WORDS = 200
    }

    // 학습한 단어(대기 제외 모든 단어들의 갯수)
    val studiedWords: Int
        get() = stage1 + stage2 + stage3 + stage4 + stage5 + stage6

    // 총 단어수 상수 사용
    val totalWords: Int
        get() = TOTAL_WORDS
}
