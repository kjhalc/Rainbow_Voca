package com.example.englishapp.manager

import android.util.Log
import com.example.englishapp.model.Word
import com.example.englishapp.model.IndividualWordState
import com.example.englishapp.model.ReviewWord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.*

object QuizWordRepository {
    private const val TAG = "QuizWordRepository"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 현재 사용자 ID 가져오기
    private fun getCurrentUserId(): String? = auth.currentUser?.uid

    // 우선순위 단어 가져오기 (individual_states에서)
    suspend fun getPriorityWords(): List<Word> {
        val userId = getCurrentUserId() ?: return emptyList()

        return try {
            val individualStates = db.collection("users")
                .document(userId)
                .collection("individual_states")
                .orderBy("priorityScore", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val wordIds = individualStates.documents.mapNotNull { it.id }
            getWordsByIds(wordIds)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting priority words", e)
            emptyList()
        }
    }

    // 일반 단어 가져오기 (words 컬렉션에서)
    suspend fun getNormalWords(excludeIds: List<String> = emptyList()): List<Word> {
        return try {
            val query = if (excludeIds.isNotEmpty()) {
                db.collection("words")
                    .whereNotIn("id", excludeIds.map { it.removePrefix("word").toIntOrNull() ?: 0 })
            } else {
                db.collection("words")
            }

            val documents = query.get().await()
            documents.mapNotNull { doc ->
                doc.toObject(Word::class.java).apply {
                    docId = doc.id
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting normal words", e)
            emptyList()
        }
    }

    // 특정 ID들의 단어 가져오기
    private suspend fun getWordsByIds(wordIds: List<String>): List<Word> {
        if (wordIds.isEmpty()) return emptyList()

        return try {
            val words = mutableListOf<Word>()
            for (wordId in wordIds) {
                val doc = db.collection("words").document(wordId).get().await()
                doc.toObject(Word::class.java)?.let { word ->
                    word.docId = doc.id
                    words.add(word)
                }
            }
            words
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words by IDs", e)
            emptyList()
        }
    }

    // 오늘 복습할 단어들 가져오기 (review_words에서)
    suspend fun getTodayReviewWords(): List<Word> {
        val userId = getCurrentUserId() ?: return emptyList()

        return try {
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 23)
            today.set(Calendar.MINUTE, 59)
            today.set(Calendar.SECOND, 59)

            val reviewWords = db.collection("users")
                .document(userId)
                .collection("review_words")
                .whereLessThanOrEqualTo("nextReviewAt", Timestamp(today.time))
                .whereEqualTo("isMastered", false)
                .get()
                .await()

            val wordIds = reviewWords.documents.mapNotNull { it.id }
            getWordsByIds(wordIds)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today review words", e)
            emptyList()
        }
    }

    // 10분 후 복습 처리 - 정답 시
    suspend fun handleTenMinuteReviewCorrect(wordId: String): Boolean {
        val userId = getCurrentUserId() ?: return false

        return try {
            val now = Timestamp.now()
            val nextReviewDate = calculateNextReviewDate(1, now)

            val reviewWord = ReviewWord(
                stage = 1,
                lastReviewedAt = now,
                nextReviewAt = nextReviewDate,
                isMastered = false
            )

            db.collection("users")
                .document(userId)
                .collection("review_words")
                .document(wordId)
                .set(reviewWord)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling ten minute review correct", e)
            false
        }
    }

    // 복습 틀렸을 때 - individual_states로 이동
    suspend fun handleReviewIncorrect(wordId: String): Boolean {
        val userId = getCurrentUserId() ?: return false

        return try {
            // 1. review_words에서 삭제
            db.collection("users")
                .document(userId)
                .collection("review_words")
                .document(wordId)
                .delete()
                .await()

            // 2. individual_states에 추가/업데이트
            val currentState = try {
                db.collection("users")
                    .document(userId)
                    .collection("individual_states")
                    .document(wordId)
                    .get()
                    .await()
                    .toObject(IndividualWordState::class.java)
            } catch (e: Exception) {
                null
            }

            val newPriority = (currentState?.priorityScore ?: 0) + 1
            val newState = IndividualWordState(priorityScore = newPriority)

            db.collection("users")
                .document(userId)
                .collection("individual_states")
                .document(wordId)
                .set(newState)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling review incorrect", e)
            false
        }
    }

    // 누적 복습 정답 처리
    suspend fun handleCumulativeReviewCorrect(wordId: String, currentStage: Int): Boolean {
        val userId = getCurrentUserId() ?: return false

        return try {
            val now = Timestamp.now()
            val nextStage = currentStage + 1
            val isMastered = nextStage > 6 // 28일 단계 완료 시

            val nextReviewDate = if (isMastered) {
                // 마스터된 단어는 매우 먼 미래로 설정
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.YEAR, 10)
                Timestamp(calendar.time)
            } else {
                calculateNextReviewDate(nextStage, now)
            }

            val reviewWord = ReviewWord(
                stage = if (isMastered) 6 else nextStage,
                lastReviewedAt = now,
                nextReviewAt = nextReviewDate,
                isMastered = isMastered
            )

            db.collection("users")
                .document(userId)
                .collection("review_words")
                .document(wordId)
                .set(reviewWord)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling cumulative review correct", e)
            false
        }
    }

    // 다음 복습 날짜 계산
    private fun calculateNextReviewDate(stage: Int, lastReviewedAt: Timestamp): Timestamp {
        val intervals = intArrayOf(1, 3, 7, 14, 21, 28)
        val dayInterval = if (stage < intervals.size) intervals[stage] else 28

        val calendar = Calendar.getInstance()
        calendar.time = lastReviewedAt.toDate()
        calendar.add(Calendar.DAY_OF_MONTH, dayInterval)

        return Timestamp(calendar.time)
    }
}