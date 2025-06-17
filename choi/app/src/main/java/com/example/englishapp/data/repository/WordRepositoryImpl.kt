package com.example.englishapp.data.repository

import android.util.Log
import com.example.englishapp.model.IndividualWordState
import com.example.englishapp.model.ReviewWord
import com.example.englishapp.model.UserProfile
import com.example.englishapp.model.Word
// import com.example.englishapp.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max


class WordRepositoryImpl(
    private val db: FirebaseFirestore = Firebase.firestore
) : WordRepository {


    // 예상 완료일 계산에 필요한 단어의 상태를 통합 관리하기 위한 내부 데이터 클래스
    private data class UserWordStatus(
        val docId: String,
        val reviewStage: Int // -1: 미학습, 0: 10분 복습, 1~6: 누적 복습 단계
    )




    companion object {
        private const val TAG = "WordRepositoryImpl"

        // 200개로 고정 시켜놓음
        private const val TOTAL_WORDS = 200

        // Firestore 컬렉션 이름들
        private const val USERS_COLLECTION = "users" // 사용자 정보
        private const val INDIVIDUAL_WORD_STATES_COLLECTION = "individual_states" // 우선순위 학습
        private const val REVIEW_WORDS_COLLECTION = "review_words" // 복습 단어
        private const val WORDS_COLLECTION = "words" // 전체 단어

        // 메인 UI에 표현하기 용이하기 위해 컬렉션 추가
        private const val STUDY_HISTORY_COLLECTION = "study_history" // 학습 기록 컬렉션

        // 테스트용 플래그 - true면 복습 간격을 분단위로 단축, false면 실제 시나리오
        private const val SHORTEN_REVIEW_INTERVALS_FOR_TESTING = true
    }

    // 실시간 리스너 관리용
    private val userProfileListeners = mutableMapOf<String, ListenerRegistration>()
    private val reviewCountListeners = mutableMapOf<String, ListenerRegistration>()

    private val listenerRegistrations = mutableMapOf<String, ListenerRegistration>()

    // 사용자 프로필 조회
    override suspend fun getUserProfile(userId: String): UserProfile? {
        if (userId.isEmpty()) {
            Log.w(TAG, "getUserProfile: userId is empty.")
            return null
        }
        return try {
            // Firestore에서 사용자 문서를 가져와 UserProfile 객체로 변환
            db.collection(USERS_COLLECTION).document(userId)
                .get()
                .await() // 코루틴 suspend 함수로 비동기 처리
                .toObject(UserProfile::class.java) // 자동 역직렬화
        } catch (e: Exception) {
            // 예외 처리
            Log.e(TAG, "Error getting user profile for $userId", e)
            null
        }
    }

    // hasStudiedToday -> 3개 다 완료한 경우
    override suspend fun updateUserStudiedTodayFlag(userId: String, hasStudied: Boolean): Boolean {
        if (userId.isEmpty()) {
            Log.w(TAG, "updateUserStudiedTodayFlag: userId is empty.")
            return false
        }
        return try {
            // hasStudied만 업데이트 - 전체 문서를 덮어쓰지 않음
            db.collection(USERS_COLLECTION).document(userId)
                .update("hasStudiedToday", hasStudied).await()
            Log.i(TAG, "User $userId 'hasStudiedToday' updated to $hasStudied in Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating hasStudiedToday for $userId to $hasStudied in Firestore", e)
            false
        }
    }


    // 우선순위에서 단어 가져오기
    private suspend fun getPriorityWordsInternal(userId: String, limit: Int): List<Word> {
        if (userId.isEmpty() || limit <= 0) return emptyList()
        val words = mutableListOf<Word>()
        try {
            // individual_states에서 priorityScore 높은 순으로 정렬
            // priorityScore가 높을수록 더 많이 틀린 단어이므로 우선 학습 대상
            val priorityWordStatesSnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(INDIVIDUAL_WORD_STATES_COLLECTION)
                .orderBy("priorityScore", Query.Direction.DESCENDING)
                .limit(limit.toLong()) // 필요한 개수만큼만 가져오기 (성능 최적화)
                .get().await()

            // 각 문서 ID로 실제 Word 정보 조회
            // individual_states에는 wordDocId와 priorityScore만 있고, 실제 단어 정보는 words 컬렉션에 있음
            for (docState in priorityWordStatesSnapshot.documents) {
                val wordDocId = docState.id
                // words 컬렉션에서 실제 단어 정보 가져오기
                getWordByDocId(wordDocId)?.let { word ->
                    words.add(word)
                } ?: Log.w(TAG, "Priority word (docId: $wordDocId) details not found from 'words' collection for user $userId.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching priority word states for $userId", e)
        }
        Log.d(TAG, "Fetched ${words.size} priority words from individual_states for user $userId.")
        return words // 우선순위대로 정렬된 상태 유지
    }

    // 우선순위 단어 가져오고 나머지 단어 가져오기
    private suspend fun getNewWordsInternal(userId: String, excludeDocIds: List<String>, limit: Int): List<Word> {
        if (userId.isEmpty() || limit <= 0) return emptyList()
        val newWords = mutableListOf<Word>()

        // 중복 제거 - excludeDocIds에 같은 ID가 여러 번 들어있을 수 있음
        val distinctExcludeIds = excludeDocIds.distinct()
        Log.d(TAG, "getNewWordsInternal for user $userId: Fetching $limit new words, excluding ${distinctExcludeIds.size} IDs.")

        try {
            // 충분히 많은 수의 후보를 가져와서 클라이언트에서 섞고 선택

            // 필요한 수의 10배 또는 최소 50개를 가져옴 - 제외할 단어들을 고려한 여유분
            val candidateFetchLimit = (limit * 10).coerceAtLeast(50) // 필요한 수의 10배 또는 최소 50개
            val allSourceWordsSnapshot = db.collection(WORDS_COLLECTION)
                // .orderBy(FieldPath.documentId()) // Firestore 기본 정렬 외에 다른 정렬 추가 가능
                .limit(candidateFetchLimit.toLong())
                .get().await()

            // 제외 목록에 없는 단어들만 필터링
            val candidates = mutableListOf<Word>()
            for (docWord in allSourceWordsSnapshot.documents) {
                val wordDocId = docWord.id
                // 이미 학습 중이거나 우선순위에 있는 단어는 제외
                if (!distinctExcludeIds.contains(wordDocId)) {
                    docWord.toObject(Word::class.java)?.let { word ->
                        word.docId = wordDocId // docId 필드 설정
                        candidates.add(word)
                    }
                }
            }

            candidates.shuffle() // 가져온 후보 단어들을 랜덤으로 섞음
            newWords.addAll(candidates.take(limit)) // 필요한 만큼만 선택

            // 충분한 단어를 못 가져온 경우 경고
            if (newWords.size < limit) {
                Log.w(TAG, "getNewWordsInternal for user $userId: Could not fetch enough new words. Requested: $limit, Fetched: ${newWords.size} after exclusions and shuffling.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching new words for user $userId, excluding ${distinctExcludeIds.size} ids.", e)
        }
        Log.d(TAG, "Fetched ${newWords.size} (randomly selected) new words for user $userId.")
        return newWords // 이미 섞여서 필요한 만큼만 가져온 상태
    }

    // 오늘의 학습 단어 선택 로직
    override suspend fun getWordsForTodayLearning(userId: String, dailyGoal: Int): List<Word> {
        if (userId.isEmpty() || dailyGoal <= 0) {
            Log.w(TAG, "getWordsForTodayLearning: Invalid input. userId: $userId, dailyGoal: $dailyGoal")
            return emptyList()
        }
        Log.i(TAG, "Starting getWordsForTodayLearning for user $userId with dailyGoal: $dailyGoal")

        // 최종 학습 리스트
        val finalLearningList = mutableListOf<Word>()
        // 신규 단어 선택시 제외할 ID 목록
        val idsToExcludeForNewWords = mutableSetOf<String>()

        // 1. individual_states에서 우선순위 높은 단어부터 가져오기 (순서 유지)
        val priorityWords = getPriorityWordsInternal(userId, dailyGoal)
        finalLearningList.addAll(priorityWords)
        // 우선순위 단어들은 신규 단어 선택에서 제외
        priorityWords.forEach { it.docId?.let { id -> idsToExcludeForNewWords.add(id) } }
        Log.d(TAG, "getWordsForTodayLearning: Added ${priorityWords.size} priority words. These will appear first.")

        // 2. 현재 review_words에 있는 모든 단어 ID도 신규 단어 제외 목록에 추가
        // 이미 복습 사이클에 들어간 단어는 오늘의 학습에서 제외
        try {
            val reviewSnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(REVIEW_WORDS_COLLECTION).get().await()
            reviewSnapshot.documents.forEach { doc -> idsToExcludeForNewWords.add(doc.id) }
        } catch (e: Exception) {
            Log.w(TAG, "getWordsForTodayLearning: Error fetching review_words IDs for exclusion.", e)
        }

        // 3. 목표치(dailyGoal)에 미달 시, words 컬렉션에서 "랜덤으로" 신규 단어 가져오기
        val remainingGoal = dailyGoal - finalLearningList.size
        if (remainingGoal > 0) {
            Log.d(TAG, "getWordsForTodayLearning: Remaining goal for new words is $remainingGoal.")
            // 내부에서 랜덤으로 섞기
            val newWords = getNewWordsInternal(userId, idsToExcludeForNewWords.toList(), remainingGoal) // 내부에서 섞여서 옴
            finalLearningList.addAll(newWords) // 우선순위 단어 뒤에 추가
            Log.d(TAG, "getWordsForTodayLearning: Added ${newWords.size} new (randomized) words.")
        } else {
            Log.d(TAG, "getWordsForTodayLearning: Daily goal met with priority words. No new words needed.")
        }

        // 최종 목록은 이미 우선순위 단어 + 랜덤 신규 단어 순서이며, dailyGoal 개수만큼만.
        // distinctBy는 혹시 모를 중복 제거 (priorityWords와 newWords 간에 발생할 일은 거의 없지만 안전장치)
        // take(dailyGoal)도 이미 내부 로직에서 처리되지만 최종 확인
        val result = finalLearningList.distinctBy { it.docId }.take(dailyGoal)
        Log.i(TAG, "getWordsForTodayLearning: Total ${result.size} words prepared for today's learning for user $userId. Priority words first, then randomized new words.")
        return result
    }


    // 10분 복습 설정
    override suspend fun setWordsForTenMinReview(userId: String, wordDocIds: List<String>): Boolean {
        if (userId.isEmpty() || wordDocIds.isEmpty()) {
            Log.w(TAG, "setWordsForTenMinReview: No user or words to set. User: $userId, WordCount: ${wordDocIds.size}")
            return false
        }
        val batch = db.batch() // Firestore 배치로 원자성 보장
        val now = Timestamp.now()

        // 복습 시간 계산 (테스트 모드에서는 즉시)
        val calendar = Calendar.getInstance().apply { time = now.toDate() }
        if (SHORTEN_REVIEW_INTERVALS_FOR_TESTING) {
            calendar.add(Calendar.MINUTE, 1) // 1분후로 테스트
            Log.d(TAG, "TEST MODE (setWordsForTenMinReview): nextReviewAt for stage 0 words set to be immediate-like for user $userId.")
        } else {
            calendar.add(Calendar.MINUTE, 10) // 10분 후
        }
        val nextReviewAtToUse = Timestamp(calendar.time)

        Log.i(TAG, "Setting ${wordDocIds.size} words to '10-min Quiz Pending' (stage=0) for user $userId.")

        // 각 단어에 대해 배치 작업 추가
        for (wordDocId in wordDocIds) {
            // 1. review_words에 stage=0으로 추가
            val reviewWordRef = db.collection(USERS_COLLECTION).document(userId)
                .collection(REVIEW_WORDS_COLLECTION).document(wordDocId)
            val reviewWordData = ReviewWord(
                stage = 0, // 10분 복습 대기 상태
                lastReviewedAt = now,
                nextReviewAt = nextReviewAtToUse,
                isMastered = false
            )
            batch.set(reviewWordRef, reviewWordData)

            // 2. individual_states에서 삭제 (있다면)
            val individualStateRef = db.collection(USERS_COLLECTION).document(userId)
                .collection(INDIVIDUAL_WORD_STATES_COLLECTION).document(wordDocId)
            batch.delete(individualStateRef)
        }

        val userDocRef = db.collection(USERS_COLLECTION).document(userId)
        batch.update(userDocRef, "isTodayLearningComplete", true)

        // 배치 실행
        return try {
            batch.commit().await()
            Log.i(TAG, "Successfully set ${wordDocIds.size} words to stage=0 for user $userId.")

            // 검증 로직 - 실제로 잘 설정되었는지 확인
            val checkWords = getWordsForTenMinReview(userId)
            Log.d(TAG, "VERIFICATION after setWordsForTenMinReview for user $userId: Found ${checkWords.size} words for 10-min review.")
            val newlySetAndFetchedCount = checkWords.count { wordDocIds.contains(it.docId) }

            // 설정한 단어 수와 실제 조회된 단어 수가 다르면 경고
            if (wordDocIds.isNotEmpty() && newlySetAndFetchedCount != wordDocIds.size) {
                Log.w(TAG, "VERIFICATION WARNING for user $userId: Mismatch in count of newly set words immediately fetched. Expected: ${wordDocIds.size}, Fetched for new: ${newlySetAndFetchedCount}. Total fetched (stage=0): ${checkWords.size}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting words to stage=0 for 10-min review for user $userId", e)
            false
        }
    }

    // 10분후 복습에 사용될 단어 조회
    override suspend fun getWordsForTenMinReview(userId: String): List<Word> {
        if (userId.isEmpty()) {
            Log.w(TAG, "getWordsForTenMinReview: userId is empty.")
            return emptyList()
        }
        val wordsToReview = mutableListOf<Word>()
        try {
            Log.d(TAG, "Fetching '10-min Quiz Pending' words (stage=0) for user $userId.")

            // stage가 정확히 0인 단어들만 조회 - 10분 복습 대기 중인 단어들
            val querySnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(REVIEW_WORDS_COLLECTION)
                .whereEqualTo("stage", 0)
                .get().await()
            Log.d(TAG, "Found ${querySnapshot.size()} documents with stage=0 for user $userId.")

            // 각 문선의 ID로 words 컬렉션에서 실제 단어 정보 가져오기
            for (doc in querySnapshot.documents) {
                val wordDocId = doc.id
                getWordByDocId(wordDocId)?.let { wordsToReview.add(it) }
                    ?: Log.w(TAG, "Could not fetch Word details for docId (stage=0): $wordDocId for user $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words for 10-min review (stage=0) for user $userId", e)
        }
        Log.i(TAG, "Returning ${wordsToReview.size} words for 10-min review (stage=0) for user $userId.")
        return wordsToReview
    }

    // 테스트용 메서드 - 10분 복습 대기 중인 모든 단어 삭제
    override suspend fun clearPendingTenMinReviewWords(userId: String): Boolean {
        if (userId.isEmpty()) {
            Log.w(TAG, "clearPendingTenMinReviewWords: userId is empty.")
            return false
        }
        Log.i(TAG, "Attempting to clear ALL pending 10-min review words (stage=0) for user $userId FOR TESTING.")
        try {
            // stage 0인 모든 문서 조회
            val querySnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(REVIEW_WORDS_COLLECTION)
                .whereEqualTo("stage", 0)
                .get().await()

            if (querySnapshot.isEmpty) {
                Log.i(TAG, "No pending 10-min review words (stage=0) found to clear for user $userId.")
                return true
            }

            // 배치로 모든 문서 삭제
            val batch = db.batch()
            for (doc in querySnapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Log.i(TAG, "Successfully cleared ${querySnapshot.size()} pending 10-min review words (stage=0) for user $userId.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing pending 10-min review words (stage=0) for user $userId", e)
            return false
        }
    }

    // "10분 후 복습" 정답 처리
    override suspend fun moveTenMinReviewWordToNextStage(userId: String, wordDocId: String): Boolean {
        if (userId.isEmpty() || wordDocId.isEmpty()) return false

        // 👇 여러 문서를 동시에 안전하게 수정하기 위해 WriteBatch를 생성합니다.
        val batch = db.batch()

        // 1. review_words 문서 업데이트 준비
        val reviewWordRef = db.collection(USERS_COLLECTION).document(userId)
            .collection(REVIEW_WORDS_COLLECTION).document(wordDocId)

        val now = Timestamp.now()
        val calendar = Calendar.getInstance().apply { time = now.toDate() }

        if (SHORTEN_REVIEW_INTERVALS_FOR_TESTING) {
            calendar.add(Calendar.MINUTE, 1)
        } else {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val nextReviewAtForStage1 = Timestamp(calendar.time)

        val updates = mapOf(
            "stage" to 1,
            "lastReviewedAt" to now,
            "nextReviewAt" to nextReviewAtForStage1,
            "isMastered" to false
        )
        // ✨ 작업을 바로 실행하지 않고, batch에 추가합니다.
        batch.update(reviewWordRef, updates)

        // 2.  users 문서에 'AI 독해용 단어 ID' 추가 준비
        val userDocRef = db.collection(USERS_COLLECTION).document(userId)
        // ✨ FieldValue.arrayUnion()을 사용하여 batch에 작업을 추가합니다.
        batch.update(userDocRef, "wordsForAiReadingToday", FieldValue.arrayUnion(wordDocId))

        // 3.  준비된 모든 작업을 한 번에 실행합니다.
        return try {
            batch.commit().await() // batch 실행
            Log.i(TAG, "Word $wordDocId (user $userId) moved to stage=1 AND added to AI reading list.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch update for stage progression and AI list for user $userId", e)
            false
        }
    }

    // "10분후 복습" 오답시 -> 개인용 단어장으로 이동
    override suspend fun moveTenMinReviewWordToIndividualStateOnIncorrect(userId: String, wordDocId: String): Boolean {
        if (userId.isEmpty() || wordDocId.isEmpty()) return false

        // 배치 작업 준비
        val batch: WriteBatch = db.batch()

        // review_words에서 삭제
        val reviewWordRef = db.collection(USERS_COLLECTION).document(userId)
            .collection(REVIEW_WORDS_COLLECTION).document(wordDocId)
        batch.delete(reviewWordRef)

        // individual_states에 추가 (우선 순위 증가)
        val individualStateRef = db.collection(USERS_COLLECTION).document(userId)
            .collection(INDIVIDUAL_WORD_STATES_COLLECTION).document(wordDocId)
        var newPriority = 1
        try {
            // 기존에 individual_states에 있었는지 확인
            val currentStateDoc = individualStateRef.get().await()

            // 이미 존재하는 경우
            if (currentStateDoc.exists()) {
                // 기존 우선순위+1 -> 더 자주 틀린 단어
                val currentPriority = currentStateDoc.getLong("priorityScore") ?: 0L
                newPriority = (currentPriority + 1).toInt()
            }
            // 새로운 상태로 설정
            val newState = IndividualWordState(priorityScore = newPriority)
            batch.set(individualStateRef, newState)

            // 배치 실행 - 삭제와 추가가 원자적으로 수행됨
            batch.commit().await()
            Log.i(TAG, "Word $wordDocId (user $userId) moved from stage=0 to individual_states (priority: $newPriority) due to incorrect 10-min review.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error moving word $wordDocId from stage=0 to individual_state for user $userId", e)
            return false
        }
    }

    // 누적 복습 대상 조회
    override suspend fun getWordsForCumulativeReview(userId: String): List<Word> {
        if (userId.isEmpty()) return emptyList()
        val wordsToReview = mutableListOf<Word>()
        try {
            val now = Timestamp.now()
            Log.d(TAG, "Fetching cumulative review words for user $userId. Current time: ${now.toDate()}")

            // 복합 쿼리 - 여러 조건을 동시에 만족하는 문서들 조회
            val querySnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(REVIEW_WORDS_COLLECTION)
                .whereGreaterThan("stage", 0) // stage > 0 (10분 복습 제외)
                .whereEqualTo("isMastered", false) // 아직 마스터 X (최종단계 도달 X)
                .whereLessThanOrEqualTo("nextReviewAt", now) // 복습 시간 도래 (서버에서 제공해주는 시간을 nextReviewAt을 사용하여 표현)
                .orderBy("nextReviewAt") // 오래된 것부터
                .get().await()

            Log.d(TAG, "Cumulative review query found ${querySnapshot.size()} words for user $userId (stage > 0, not mastered, due for review).")

           // Word 정보 조회
            for (doc in querySnapshot.documents) {
                val wordDocId = doc.id
                Log.d(TAG, "Cumulative review candidate: wordId=${doc.id}, stage=${doc.getLong("stage")}, nextReviewAt=${doc.getTimestamp("nextReviewAt")?.toDate()}")
                getWordByDocId(wordDocId)?.let { wordsToReview.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words for cumulative review for $userId", e)
        }
        Log.i(TAG, "Returning ${wordsToReview.size} words for cumulative review for user $userId")
        return wordsToReview
    }


    // 복습 간격 계산 -> 현재는 테스트로 해놓음 위에 테스트 플래그 false로 바꾸면 실제 시나리오대로 작동
    private fun calculateNextReviewDate(currentStage: Int, lastReviewedTimestamp: Timestamp): Timestamp? {
        val calendar = Calendar.getInstance().apply { time = lastReviewedTimestamp.toDate() }
        if (SHORTEN_REVIEW_INTERVALS_FOR_TESTING) {
            //Log.d(TAG, "TEST MODE (calculateNextReviewDate): Calculating shortened next review date for currentStage: $currentStage for user $userId")

            // 테스트 모드 : 모든 단계 1분으로
            val minutesToAdd = when (currentStage) {
                0 -> 1;
                1 -> 1;
                2 -> 1;
                3 -> 1;
                4 -> 1;
                5 -> 1
                else -> { return if (currentStage >= 6) null else Timestamp(lastReviewedTimestamp.toDate()) }
            }
            calendar.add(Calendar.MINUTE, minutesToAdd)
        } else {
            // 실제 운영 모드 : 점진적 간격 증가 (우리가 설정한 실제로)
            val reviewIntervalsDays = when (currentStage) {
                0 -> 1;
                1 -> 3;
                2 -> 7;
                3 -> 14;
                4 -> 21;
                5 -> 28
                else -> {
                    //Log.w(TAG, "calculateNextReviewDate: currentStage $currentStage has no next review or is invalid for user $userId.")
                    return if (currentStage >= 6) null else Timestamp(lastReviewedTimestamp.toDate())
                }
            }
            calendar.add(Calendar.DAY_OF_YEAR, reviewIntervalsDays)
        }
        //Log.d(TAG, "Calculated nextReviewAt for user $userId, moving from stage $currentStage to ${currentStage + 1}: ${calendar.time}")
        return Timestamp(calendar.time)
    }

    // 누적 복습 단계 증가 - 누적 복습 정답시
    override suspend fun updateCumulativeReviewWordOnCorrect(userId: String, wordDocId: String, currentReviewWord: ReviewWord): Boolean {
        if (userId.isEmpty() || wordDocId.isEmpty()) return false
        val reviewWordRef = db.collection(USERS_COLLECTION).document(userId)
            .collection(REVIEW_WORDS_COLLECTION).document(wordDocId)
        val newStage = currentReviewWord.stage + 1 // 스테이지+1
        val now = Timestamp.now()

        // 업데이트할 필드 준비 - mutableMap 사용하여 조건부 필드 추가
        val updates = mutableMapOf<String, Any?>("lastReviewedAt" to now)

        // 6단계 완료 = 마스터 달성
        if (newStage > 6) {
            updates["stage"] = 6; updates["isMastered"] = true; updates["nextReviewAt"] = null
            Log.i(TAG, "Word $wordDocId MASTERED (stage 6 complete) for user $userId.")
        } else {
            // 아직 마스터 전 - 다음 단계로 진행
            updates["stage"] = newStage
            updates["nextReviewAt"] = calculateNextReviewDate(currentReviewWord.stage, now)
            updates["isMastered"] = false
            Log.i(TAG, "Word $wordDocId (user $userId) moved to stage $newStage for cumulative review. Next review: ${updates["nextReviewAt"]?.let { (it as Timestamp).toDate() }}")
        }
        return try {
            reviewWordRef.update(updates).await(); true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cumulative review word $wordDocId to stage $newStage for user $userId", e); false
        }
    }

    // 누적 복습 오답시
    override suspend fun moveCumulativeReviewWordToIndividualStateOnIncorrect(userId: String, wordDocId: String): Boolean {
        if (userId.isEmpty() || wordDocId.isEmpty()) return false

        // 10분 복습 오답 처리와 동일한 로직
        val batch: WriteBatch = db.batch()

        // review_words에서 삭제
        val reviewWordRef = db.collection(USERS_COLLECTION).document(userId)
            .collection(REVIEW_WORDS_COLLECTION).document(wordDocId)
        batch.delete(reviewWordRef)

        // individual_states에 추가 (우선순위 증가)
        val individualStateRef = db.collection(USERS_COLLECTION).document(userId)
            .collection(INDIVIDUAL_WORD_STATES_COLLECTION).document(wordDocId)
        var newPriority = 1
        // "10분후 복습"과 동일하게 기존 우선순위 확인 또 틀리면 우선순위 더 증가
        try {
            val currentStateDoc = individualStateRef.get().await()
            if (currentStateDoc.exists()) {
                newPriority = (currentStateDoc.getLong("priorityScore") ?: 0L).toInt() + 1
            }
            batch.set(individualStateRef, IndividualWordState(priorityScore = newPriority))
            batch.commit().await()
            Log.i(
                TAG,
                "Word $wordDocId (user $userId) moved from cumulative review to individual_states (priority: $newPriority)."
            )
            return true
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error moving word $wordDocId from cumulative review to individual_state for user $userId",
                e
            ); return false
        }
    }

    // --- 퀴즈 선택지용 새 함수 ---
    override suspend fun getRandomWordMeanings(count: Int, excludeMeanings: List<String>): List<String> {
        if (count <= 0) return emptyList()

        // Set 사용하여 중복 방지
        val meanings = mutableSetOf<String>() // 중복 방지
        excludeMeanings.forEach { meanings.add(it) } // 제외할 뜻 미리 추가 (선택지에 안 나오도록)

        val meaningsToReturn = mutableListOf<String>()

        try {
            // Firestore에서 랜덤으로 가져오기는 직접 지원하지 않으므로,
            // 충분한 양의 문서를 가져와서 클라이언트에서 섞고 선택합니다.
            // WORDS_COLLECTION의 전체 문서 수를 안다면 더 효율적인 샘플링 가능.
            // 여기서는 일단 limit을 크게 설정하여 가져옵니다.
            val candidateWordsSnapshot = db.collection(WORDS_COLLECTION)
                .limit((count * 10).toLong().coerceAtLeast(50)) // 필요한 수의 10배 또는 최소 50개
                .get().await()

            // 모든 단어 뜻을 추출하고 중복 제거 후 섞기
            val allMeaningsFromSource = candidateWordsSnapshot.documents.mapNotNull { it.getString("word_mean") }.distinct().shuffled()

            // 필요한 개수만큼 선택
            for (meaning in allMeaningsFromSource) {
                if (meaningsToReturn.size >= count) break
                if (!meanings.contains(meaning)) { // excludeMeanings 및 이미 추가된 뜻 제외
                    meaningsToReturn.add(meaning)
                    meanings.add(meaning) // 다음 중복 체크를 위해 추가
                }
            }

            // 만약 아직도 부족하다면, 매우 일반적인 더미 오답으로 채울 수 있으나, 최대한 실제 데이터로 채우려고 시도.
            var dummyCounter = 1
            while (meaningsToReturn.size < count) {
                val dummyMeaning = "다른 뜻 ${dummyCounter++}"
                if (!meanings.contains(dummyMeaning)) {
                    meaningsToReturn.add(dummyMeaning)
                    meanings.add(dummyMeaning)
                }
                if (dummyCounter > 20) break // 무한 루프 방지
            }
            Log.d(TAG, "getRandomWordMeanings: Requested $count, returned ${meaningsToReturn.size}. Excluded ${excludeMeanings.size} initially.")

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching random word meanings", e)
            // 오류 발생 시 더미 데이터로 채우기 (개선 필요)
            while (meaningsToReturn.size < count) {
                meaningsToReturn.add("오류 발생 ${meaningsToReturn.size + 1}")
            }
        }
        return meaningsToReturn
    }


    // --- 메인 화면 그래프 및 상태 관련 ---
    override suspend fun getTotalWordCountInSource(): Int { /* 이전과 동일 */
        return try {
            // count() 집계 쿼리 - 문서 내용 가져오지 않고 개수만 효율적으로 계산
            db.collection(WORDS_COLLECTION).count().get(AggregateSource.SERVER).await().count.toInt()
        } catch (e: Exception) { 0 }
    }
    override suspend fun getReviewWordCountsByStageMap(userId: String): Map<Int, Int> { /* 이전과 동일 */
        if (userId.isEmpty()) return emptyMap()
        val countsMap = mutableMapOf<Int, Int>()
        try {
            // 전체 단어 수
            val totalWordsInSource = getTotalWordCountInSource()
            var wordsInReviewCyclesOrMasteredCount = 0

            // stage >=1인 모든 단어 개수
            val reviewWordsSnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(REVIEW_WORDS_COLLECTION).whereGreaterThanOrEqualTo("stage", 1).get().await()
            wordsInReviewCyclesOrMasteredCount = reviewWordsSnapshot.size()

            // stage 0(미학습) = 전체 - (복습중 + 마스터)
            countsMap[0] = maxOf(0, totalWordsInSource - wordsInReviewCyclesOrMasteredCount)
            for (stage in 1..6) {
                countsMap[stage] = getReviewWordsCountByStage(userId, stage, false)
            }
        } catch (e: Exception) { (0..6).forEach { countsMap[it] = 0 } }
        return countsMap
    }


    // 복습 단계별 카운트 집계
    override suspend fun getReviewWordsCountByStage(userId: String, stage: Int, isMastered: Boolean): Int { /* 이전과 동일 */
        if (userId.isEmpty()) return 0
        return try {
            db.collection(USERS_COLLECTION).document(userId).collection(REVIEW_WORDS_COLLECTION)
                .whereEqualTo("stage", stage).whereEqualTo("isMastered", isMastered)
                .count().get(AggregateSource.SERVER).await().count.toInt()
        } catch (e: Exception) { 0 }
    }
    override suspend fun getCurrentlyReviewingWordsCount(userId: String): Int { /* 이전과 동일 */
        if (userId.isEmpty()) return 0
        return try {
            // 현재 복습 중인 단어 : stage 1~6, 마스터 X
            db.collection(USERS_COLLECTION).document(userId).collection(REVIEW_WORDS_COLLECTION)
                .whereGreaterThanOrEqualTo("stage", 1).whereLessThanOrEqualTo("stage", 6)
                .whereEqualTo("isMastered", false)
                .count().get(AggregateSource.SERVER).await().count.toInt()
        } catch (e: Exception) { 0 }
    }

    // 헬퍼 메서드 - 단어 정보 조회
    override suspend fun getWordByDocId(wordDocId: String): Word? { /* 이전과 동일 */
        if (wordDocId.isEmpty()) return null
        return try {
            db.collection(WORDS_COLLECTION).document(wordDocId).get().await()
                .toObject(Word::class.java)?.apply {
                    // docId는 Firestore 문서에는 없으므로 여기서 설정
                    this.docId = wordDocId }
        } catch (e: Exception) { null }
    }

    // 복습용 단어 가져오기
    override suspend fun getReviewWord(userId: String, wordDocId: String): ReviewWord? { /* 이전과 동일 */
        if (userId.isEmpty() || wordDocId.isEmpty()) return null
        return try {
            db.collection(USERS_COLLECTION).document(userId).collection(REVIEW_WORDS_COLLECTION)
                .document(wordDocId).get().await().toObject(ReviewWord::class.java)
        } catch (e: Exception) { null }
    }
    override suspend fun getIndividualWordState(userId: String, wordDocId: String): IndividualWordState? { /* 이전과 동일 */
        if (userId.isEmpty() || wordDocId.isEmpty()) return null
        return try {
            db.collection(USERS_COLLECTION).document(userId).collection(INDIVIDUAL_WORD_STATES_COLLECTION)
                .document(wordDocId).get().await().toObject(IndividualWordState::class.java)
        } catch (e: Exception) { null }
    }
    override suspend fun updateUserDailyWordGoalInFirestore(userId: String, newGoal: Int): Boolean {
        // 요청 파라미터에 대한 기본적인 유효성 검사를 수행
        // userId가 비어있거나, newGoal 값이 허용된 범위(예: 10~50)를 벗어나면 실제 DB 작업을 수행하지 않고 실패를 반환
        // 이러한 검증은 서버 API 레벨에서도 동일하게 이루어져야 데이터의 정합성을 보장할 수 있음
        if (userId.isBlank() || userId == "UNKNOWN_USER") {
            Log.w(TAG, "updateUserDailyWordGoalInFirestore: Invalid userId provided ($userId).")
            return false
        }
        // 테스트를 위해서 1~5개로 제한
        if (newGoal < 1 || newGoal > 5) { // 예시: 학습 목표는 10개에서 50개 사이로 가정
            Log.w(TAG, "updateUserDailyWordGoalInFirestore: Invalid newGoal value ($newGoal). Must be between 10 and 50.")
            return false
        }

        return try {
            // Firestore의 'users' 컬렉션에서 해당 userId를 문서 ID로 가지는 문서를 찾아,
            // 'dailyWordGoal' 필드의 값을 newGoal로 업데이트
            // UserProfile 데이터 모델에 'dailyWordGoal' 필드가 Int 타입으로 정의
            db.collection(USERS_COLLECTION).document(userId)
                .update("dailyWordGoal", newGoal) // "dailyWordGoal"은 UserProfile 모델의 필드명과 일치해야 함
                .await() // 코루틴으로 비동기 작업이 완료될 때까지 대기
            Log.i(TAG, "User $userId dailyWordGoal successfully updated to $newGoal in Firestore.")
            true // 업데이트 성공
        } catch (e: Exception) {
            // Firestore 업데이트 중 예외 발생 시 (네트워크 오류, 권한 문제 등)
            Log.e(TAG, "Error updating dailyWordGoal for user $userId to $newGoal in Firestore", e)
            false // 업데이트 실패
        }
    }

    // 1️⃣ 오늘의 학습 갯수
//    override fun observeUserProfile(userId: String, callback: (UserProfile?) -> Unit) {
//        userProfileListeners[userId]?.remove()
//
//        val listener = db.collection(USERS_COLLECTION).document(userId)
//            .addSnapshotListener { snapshot, error ->
//                if (error != null) {
//                    Log.e(TAG, "UserProfile listen failed for $userId", error)
//                    callback(null)
//                    return@addSnapshotListener
//                }
//
//                val userProfile = snapshot?.toObject(UserProfile::class.java)
//                Log.d(TAG, "🔥 오늘의 학습 갯수 실시간 업데이트: ${userProfile?.dailyWordGoal}개")
//                callback(userProfile)
//            }
//
//        userProfileListeners[userId] = listener
//    }
    // 2️⃣ 🔥 누적 복습 갯수 수정
//    override fun observeTodayReviewCount(userId: String, callback: (Int) -> Unit) {
//        val listener = db.collection(USERS_COLLECTION).document(userId)
//            .collection(REVIEW_WORDS_COLLECTION)
//            .whereGreaterThan("stage", 0)  // stage > 0 (10분 복습 제외)
//            .whereEqualTo("isMastered", false)  // 아직 마스터 안됨
//            .whereLessThanOrEqualTo("nextReviewAt", Timestamp.now())  // 🔥 수정: 현재시간 이전
//            .addSnapshotListener { snapshot, error ->
//                if (error != null) {
//                    Log.e(TAG, "TodayReviewCount listen failed for $userId", error)
//                    callback(0)
//                    return@addSnapshotListener
//                }
//
//                val count = snapshot?.documents?.size ?: 0
//                Log.d(TAG, "🔥 누적 복습 갯수 실시간 업데이트: ${count}개")
//                callback(count)
//            }
//
//        reviewCountListeners["todayReview_$userId"] = listener
//    }

    // [수정] 'override' 키워드를 추가합니다.
//    override fun observeReviewStageCounts(userId: String, callback: (Map<Int, Int>) -> Unit) {
//        val listener = db.collection(USERS_COLLECTION).document(userId)
//            .collection(REVIEW_WORDS_COLLECTION)
//            .addSnapshotListener { snapshot, error ->
//                if (error != null) {
//                    Log.e(TAG, "ReviewStageCounts listen failed", error)
//                    callback(emptyMap())
//                    return@addSnapshotListener
//                }
//
//                val stageMap = mutableMapOf<Int, Int>()
//                (0..6).forEach { stageMap[it] = 0 }
//
//                snapshot?.documents?.forEach { doc ->
//                    val stage = doc.getLong("stage")?.toInt() ?: 0
//                    stageMap[stage] = (stageMap[stage] ?: 0) + 1
//                }
//
//                // 이 부분은 getReviewWordCountsByStageMap의 로직과 겹치므로,
//                // 실시간 리스너에서는 순수 스테이지 카운트만 제공하는 것이 더 명확할 수 있습니다.
//                // val totalLearned = stageMap.filterKeys { it > 0 }.values.sum()
//                // stageMap[0] = max(0, TOTAL_WORDS - totalLearned)
//
//                Log.d(TAG, "🔥 복습 단계별 실시간 업데이트: $stageMap")
//                callback(stageMap)
//            }
//        // reviewCountListeners는 listenerRegistrations로 통일하여 관리하는 것이 좋습니다.
//        listenerRegistrations["stageCounts_$userId"] = listener
//    }

    // [수정] 'override' 키워드를 추가합니다.
    override fun observeTenMinReviewCount(userId: String, callback: (Int) -> Unit) {
        val listener = db.collection(USERS_COLLECTION).document(userId)
            .collection(REVIEW_WORDS_COLLECTION)
            .whereEqualTo("stage", 0)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "TenMinReviewCount listen failed", error)
                    callback(0)
                    return@addSnapshotListener
                }

                val count = snapshot?.documents?.size ?: 0
                Log.d(TAG, "🔥 10분 복습 대기 실시간 업데이트: ${count}개")
                callback(count)
            }
        listenerRegistrations["tenMin_$userId"] = listener
    }



    // 3️⃣ 예상 완료일
    private suspend fun getAllWordsForUser(userId: String): List<UserWordStatus> {
        if (userId.isEmpty()) return emptyList()

        val allUserWordsMap = mutableMapOf<String, UserWordStatus>()
        try {
            // 1. DB의 모든 단어를 가져와 '미학습(-1)' 상태로 초기 설정
            val allSourceWordsSnapshot = db.collection(WORDS_COLLECTION).get().await()
            allSourceWordsSnapshot.documents.forEach { doc ->
                allUserWordsMap[doc.id] = UserWordStatus(docId = doc.id, reviewStage = -1)
            }
            // 2. 사용자의 복습 단어 정보를 가져와 '미학습' 상태를 실제 복습 단계로 덮어쓰기
            val reviewWordsSnapshot = db.collection(USERS_COLLECTION).document(userId)
                .collection(REVIEW_WORDS_COLLECTION).get().await()
            reviewWordsSnapshot.documents.forEach { doc ->
                allUserWordsMap[doc.id] = UserWordStatus(docId = doc.id, reviewStage = doc.getLong("stage")?.toInt() ?: 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllWordsForUser 실패 for userId: $userId", e)
            return emptyList()
        }
        return allUserWordsMap.values.toList()
    }

    // 예상 완료일 최종 계산
//    override suspend fun calculateDetailedCompletionDate(
//        userId: String,
//        dailyGoal: Int,
//        averageAccuracy: Float
//    ): String {
//        return try {
//            val allWords = getAllWordsForUser(userId)
//            val stageCountMap = allWords.groupBy { it.reviewStage }
//            val unstudiedCount = allWords.count { it.reviewStage == -1 }
//            val daysToNextStage = mapOf(-1 to 0, 0 to 1, 1 to 3, 2 to 7, 3 to 14, 4 to 21, 5 to 28, 6 to 0)
//
//            val daysForUnstudied = if (dailyGoal > 0) (unstudiedCount + dailyGoal - 1) / dailyGoal else 0
//            var maxDaysToMaster = daysForUnstudied
//
//            for (stage in 0..5) {
//                val wordsInStage = stageCountMap[stage]?.size ?: 0
//                if (wordsInStage > 0) {
//                    var daysFromStageToMaster = 0
//                    for (s in stage..5) {
//                        daysFromStageToMaster += daysToNextStage[s] ?: 0
//                    }
//                    val adjustedDays = (daysFromStageToMaster * (1 + (1 - averageAccuracy))).toInt()
//                    maxDaysToMaster = max(maxDaysToMaster, adjustedDays)
//                }
//            }
//
//            val currentDate = Calendar.getInstance()
//            currentDate.add(Calendar.DAY_OF_MONTH, maxDaysToMaster)
//            SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN).format(currentDate.time)
//        } catch (e: Exception) {
//            Log.e(TAG, "[예상 완료일] 계산 실패", e)
//            "계산 중..."
//        }
//    }



// 성실도 계산 함수 -> 진도율로 수정한 상태
    @Suppress("UNCHECKED_CAST")
    override suspend fun getMonthlyDiligence(userId: String, today: Date): Int {
        val calendar = Calendar.getInstance().apply { time = today }
       val yearMonth = SimpleDateFormat("yyyy-MM", Locale.KOREAN).format(calendar.time)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        return try {
            val historyDoc = db.collection(USERS_COLLECTION).document(userId).collection(STUDY_HISTORY_COLLECTION).document(yearMonth).get().await()

          if (!historyDoc.exists()) {
              return 100
          }
            val studiedDays = historyDoc.get("studiedDays") as? List<Long> ?: return 100
            if (studiedDays.isEmpty()) return 100

            val firstStudyDay = studiedDays.minOrNull()?.toInt() ?: currentDay
           val totalDaysPassed = (currentDay - 1) - firstStudyDay + 1
            if (totalDaysPassed <= 0) return 100

            val studiedCountInPeriod = studiedDays.count { it.toInt() in firstStudyDay..(currentDay - 1) }
            val missedDays = totalDaysPassed - studiedCountInPeriod
            val diligence = 100 - (missedDays * 3)
            max(0, diligence)
       } catch (e: Exception) {
            Log.e(TAG, "[이달의 성실도] 계산 실패", e)
            100
        }
}

    override suspend fun recordStudyDay(userId: String, date: Date): Boolean {
        val calendar = Calendar.getInstance().apply { time = date }
        val yearMonth = SimpleDateFormat("yyyy-MM", Locale.KOREAN).format(calendar.time)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH).toLong()

        return try {
            val historyRef = db.collection(USERS_COLLECTION).document(userId)
                .collection(STUDY_HISTORY_COLLECTION).document(yearMonth)
            val data = mapOf("studiedDays" to FieldValue.arrayUnion(dayOfMonth))
            historyRef.set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "[학습일 기록] 실패", e)
            false
        }
    }

    // 10분 후 복습 완료 시 플래그 업데이트
    suspend fun completeTenMinReview(userId: String): Boolean {
        return try {
            db.collection(USERS_COLLECTION).document(userId)
                .update("isPostLearningReviewReady", false)
                .await()
            Log.i(TAG, "10분 후 복습 완료: isPostLearningReviewReady = false")
            true
        } catch (e: Exception) {
            Log.e(TAG, "isPostLearningReviewReady 업데이트 실패", e)
            false
        }
    }

    // hasStudiedToday 체크 및 업데이트
    suspend fun checkAndUpdateDailyCompletion(userId: String): Boolean {
        try {
            val userDoc = db.collection(USERS_COLLECTION).document(userId).get().await()
            val userData = userDoc.data ?: return false

            // 1. 오늘의 학습 완료 여부
            val todayLearningDone = userData["isTodayLearningComplete"] as? Boolean ?: false
            if (!todayLearningDone) {
                Log.d(TAG, "오늘의 학습이 아직 완료되지 않음")
                return false
            }

            // 2. 10분 후 복습 완료 여부 (isPostLearningReviewReady가 false면 완료)
            val tenMinReviewDone = !(userData["isPostLearningReviewReady"] as? Boolean ?: false)
            if (!tenMinReviewDone) {
                Log.d(TAG, "10분 후 복습이 아직 완료되지 않음")
                return false
            }

            // 3. 누적 복습 완료 여부 (오늘 복습할 단어가 없으면 완료)
            val cumulativeReviewWords = getWordsForCumulativeReview(userId)
            val cumulativeReviewDone = cumulativeReviewWords.isEmpty()
            if (!cumulativeReviewDone) {
                Log.d(TAG, "누적 복습이 아직 완료되지 않음 (${cumulativeReviewWords.size}개 남음)")
                return false
            }

            // 모두 완료했으면 hasStudiedToday를 true로
            Log.i(TAG, "✅ 모든 학습 완료! hasStudiedToday를 true로 업데이트")
            return updateUserStudiedTodayFlag(userId, true)

        } catch (e: Exception) {
            Log.e(TAG, "checkAndUpdateDailyCompletion 실패", e)
            return false
        }
    }

    override fun removeAllListeners(userId: String) {
        userProfileListeners[userId]?.remove()
        reviewCountListeners.filter { it.key.contains(userId) }.forEach { (_, listener) ->
            listener.remove()
        }
        userProfileListeners.remove(userId)
        reviewCountListeners.keys.removeAll { it.contains(userId) }
        Log.d(TAG, "🔥 실시간 리스너 모두 해제됨 for user: $userId")
    }
    override fun saveCorrectWordsForToday(userId: String, words: List<String>, onComplete: (Boolean) -> Unit) {
    if (userId.isEmpty() || words.isEmpty()) {
        onComplete(false)
        return
    }

    val data = mapOf("wordsForAiReadingToday" to words)
    Firebase.firestore.collection("users").document(userId)
        .set(data, SetOptions.merge())
        .addOnSuccessListener {
            Log.i("WordRepositoryImpl", "AI 독해용 맞은 단어 ${words.size}개 저장 완료")
            onComplete(true)
        }
        .addOnFailureListener { e ->
            Log.e("WordRepositoryImpl", "AI 독해용 단어 저장 실패: ${e.message}", e)
            onComplete(false)
        }
}







}
