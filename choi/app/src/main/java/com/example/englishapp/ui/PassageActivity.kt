package com.example.englishapp.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.englishapp.R
import com.example.englishapp.gpt.GptHelper

class PassageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passage) // ✅ 너가 이미 만든 xml과 연결

        // ❌ 닫기 버튼 처리 (뒤로가기)
        val closeBtn = findViewById<ImageButton>(R.id.button_close)
        closeBtn.setOnClickListener {
            finish() // 현재 화면 닫고 이전으로 돌아가기
        }

        val passageTextView = findViewById<TextView>(R.id.text_passage)
        val explanationTextView = findViewById<TextView>(R.id.text_explanation)
        val showExplanationButton = findViewById<Button>(R.id.button_show_explanation)

        explanationTextView.visibility = View.GONE

        // 실제 앱에서는 인텐트로 넘긴 단어를 받아올 수 있음
        //val testWords = listOf("smile", "goal", "freedom", "journey", "dream")
        val wordsFromIntent = intent.getStringArrayListExtra("correct_words") ?: listOf()

        if (wordsFromIntent.isEmpty()) {
            Log.w("PassageActivity", "넘겨받은 단어가 없음!")
            return
        }

        GptHelper.generateParagraphFromCorrectWords(wordsFromIntent) { fullText ->
            runOnUiThread {
                Log.d("PassageActivity", "GPT 응답: $fullText")

                val parts = fullText.split("해석:")
                val english = parts.getOrNull(0)?.trim() ?: "(지문 없음)"
                val korean = parts.getOrNull(1)?.trim() ?: "(해석 없음)"

                passageTextView.text = english

                showExplanationButton.setOnClickListener {
                    explanationTextView.text = korean
                    explanationTextView.visibility = View.VISIBLE
                }
            }
        }
    }
}
