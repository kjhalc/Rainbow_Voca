package com.example.englishapp.gpt

import android.util.Log
import com.example.englishapp.model.ChatRequest
import com.example.englishapp.model.ChatResponse
import com.example.englishapp.model.Message
import com.example.englishapp.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object GptHelper {

    private const val TAG = "GptHelper"
    private const val API_KEY =  "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" // TODO: 안전하게 관리할 것!

    fun generateParagraphFromCorrectWords(words: List<String>, onResult: (String) -> Unit) {
        val prompt1 = """
            You are an English teacher helping Korean middle school students learn vocabulary through short paragraph reading.

            From this list, choose 6 to 10 words that are semantically connected:
            ${words.joinToString(", ")}

            Respond with only a comma-separated list of the selected words.
        """.trimIndent()

        val api = RetrofitClient.getClient()
        val request1 = ChatRequest("gpt-4o", listOf(Message("user", prompt1)), 0.3, 100)

        api.createChatCompletion(API_KEY, request1).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val selected = response.body()?.choices?.firstOrNull()?.message?.content
                    ?.split(",")?.map { it.trim() } ?: return

                val prompt2 = """
                    You are an English tutor helping Korean middle school students learn vocabulary.

                    Using only the following words, write a 5-sentence paragraph that sounds like a casual conversation or personal monologue.
                    Each sentence should include 1 or 2 of the words.
                    Tone should be informal and spoken-style.

                    Words: ${selected.joinToString(", ")}
                """.trimIndent()

                val request2 = ChatRequest("gpt-4o", listOf(Message("user", prompt2)), 0.7, 300)

                api.createChatCompletion(API_KEY, request2).enqueue(object : Callback<ChatResponse> {
                    override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                        val result = response.body()?.choices?.firstOrNull()?.message?.content ?: ""
                        onResult(result)
                    }

                    override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                        Log.e(TAG, "GPT 문장 생성 실패", t)
                        onResult("GPT 문장 생성 실패: ${t.message}")
                    }
                })
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                Log.e(TAG, "GPT 단어 선택 실패", t)
                onResult("GPT 단어 선택 실패: ${t.message}")
            }
        })
    }
}
