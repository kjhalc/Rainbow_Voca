package com.example.englishapp.ui

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.englishapp.R

class PassageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passage) // ✅ 너가 이미 만든 xml과 연결

        // ❌ 닫기 버튼 처리 (뒤로가기)
        val closeBtn = findViewById<ImageButton>(R.id.button_close)
        closeBtn.setOnClickListener {
            finish() // 현재 화면 닫고 이전으로 돌아가기
        }
    }
}
