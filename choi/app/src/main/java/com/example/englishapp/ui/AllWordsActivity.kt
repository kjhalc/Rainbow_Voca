package com.example.englishapp.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.englishapp.R
import com.example.englishapp.utils.WordAdapter
import com.example.englishapp.viewmodel.AllWordsViewModel

class AllWordsActivity : AppCompatActivity() {

    private lateinit var wordsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnClose: ImageButton
    private lateinit var searchEditText: EditText

    // private lateinit var statsTextView: TextView
    private lateinit var noResultsTextView: TextView
    private lateinit var wordAdapter: WordAdapter

    private val viewModel: AllWordsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_words)

        initViews()
        setupRecyclerView()
        setupSearch()
        setupObservers()

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun initViews() {
        wordsRecyclerView = findViewById(R.id.wordsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        btnClose = findViewById(R.id.btn_close)
        searchEditText = findViewById(R.id.searchEditText)
        // statsTextView = findViewById(R.id.statsTextView)
        noResultsTextView = findViewById(R.id.noResultsTextView)
    }

    private fun setupRecyclerView() {
        wordAdapter = WordAdapter { word ->
            // 상호반응 테스트 겸 누르면 토스트 뜨게 해둠 ->  효과를 넣던지 주석처리(삭제)하던지 알아서
            Toast.makeText(this, "'${word.word_text}' 클릭됨", Toast.LENGTH_SHORT).show()
        }
        wordsRecyclerView.adapter = wordAdapter
        wordsRecyclerView.layoutManager = GridLayoutManager(this, calculateSpanCount())
        wordsRecyclerView.itemAnimator = DefaultItemAnimator()
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display?.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
        }
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = 320
        return (dpWidth / scalingFactor).toInt().coerceAtLeast(1)
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupObservers() {
        // isLoading LiveData는 오직 ProgressBar의 상태만 제어
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // wordsList LiveData가 목록과 '결과 없음' 텍스트의 상태를 모두 책임
        viewModel.wordsList.observe(this) { words ->
            // 어댑터에 데이터는 항상 업데이트합니다.
            // wordAdapter.updateData(words)

            // submitList()사용
            wordAdapter.submitList(words)

            // 로딩이 끝난 후에만 목록의 가시성을 판단하여 UI 충돌을 방지
            if (viewModel.isLoading.value == false) {
                if (words.isEmpty()) {
                    // 목록이 비어있으면 '결과 없음' 텍스트를 보여주고, 리스트는 숨김
                    wordsRecyclerView.visibility = View.GONE
                    noResultsTextView.visibility = View.VISIBLE
                } else {
                    // 목록이 있으면 리스트를 보여주고, '결과 없음' 텍스트는 숨깁
                    wordsRecyclerView.visibility = View.VISIBLE
                    noResultsTextView.visibility = View.GONE
                }
            }
//        viewModel.statsText.observe(this) { stats ->
//            statsTextView.text = stats
//        }
        }
    }
}