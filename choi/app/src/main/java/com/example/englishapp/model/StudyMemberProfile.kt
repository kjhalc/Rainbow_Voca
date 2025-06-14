package com.example.englishapp.model

data class StudyMemberProfile(
    val userId: Int,
    val nickname: String,
    val profileImage: String?,
    val isAttendedToday: Boolean = false,

    /**   stage 분포를 그대로 달아둡니다 (Firestore → ViewModel에서 넣어줌) */
    val stageDistribution: StageDistribution = StageDistribution(),

    /** todayWrongCount 와 1:1 동기화 */
    val wrongAnswerCount: Int = 0,

    // 랭킹용 변수
    var rankOrder: Int = 0


) {

    /** 학습한 단어 수를 stageDistribution 로부터 계산 */
    val studiedWordCount: Int
        get() = stageDistribution.studiedWords

    /** 전체 단어는 상수(200) */
    val totalWordCount: Int
        get() = StageDistribution.TOTAL_WORDS

    /** 진도율(%) */
    val progressRate: Int
        get() = (studiedWordCount * 100) / totalWordCount

    // ── ↓↓↓ 기존 색상 관련 함수는 건드리지 마세요 ↓↓↓ ──────────────────────
    fun getAttendanceColorCode(): String =
        if (isAttendedToday) "#4CAF50" else "#BDBDBD"

    fun getWrongAnswerColorCode(): String {
        return when {
            !isAttendedToday      -> "#BDBDBD"
            wrongAnswerCount == 0 -> "#4CAF50"
            else -> {
                val ratio = (wrongAnswerCount / 10f).coerceIn(0f, 1f)
                val r = (255 * (1 - ratio) + 211 * ratio).toInt()
                val g = (205 * (1 - ratio) + 47 * ratio).toInt()
                val b = (210 * (1 - ratio) + 47 * ratio).toInt()
                String.format("#%02X%02X%02X", r, g, b)
            }
        }
    }
}

