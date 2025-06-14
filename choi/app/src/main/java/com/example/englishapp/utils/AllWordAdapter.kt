package com.example.englishapp.utils // 본인의 패키지 경로로 수정

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.englishapp.R
import com.example.englishapp.model.Word

// 1. RecyclerView.Adapter 대신 ListAdapter 상속
class WordAdapter(
    private val onItemClicked: (Word) -> Unit
) : ListAdapter<Word, WordAdapter.WordViewHolder>(WordDiffCallback()) { // 2. 생성자에서 리스트를 받지 않고, DiffUtil 콜백을 전달

    // ViewHolder 클래스는 이전과 동일
    class WordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewId: TextView = view.findViewById(R.id.textViewId)
        val textViewWordText: TextView = view.findViewById(R.id.textViewWordText)
        val textViewWordMean: TextView = view.findViewById(R.id.textViewWordMean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val word = getItem(position) // 3. ListAdapter의 내장 메서드인 getItem() 사용
        holder.textViewId.text = "#${word.id}"
        holder.textViewWordText.text = word.word_text
        holder.textViewWordMean.text = word.word_mean

        holder.itemView.setOnClickListener {
            onItemClicked(word)
        }
    }

    // 4. getItemCount()와 updateData() 함수 삭제 (ListAdapter가 자동으로 처리)
}


// 5. 두 리스트의 차이점을 계산하는 DiffUtil.ItemCallback 클래스 구현
class WordDiffCallback : DiffUtil.ItemCallback<Word>() {
    // 아이템의 고유 ID(Key)가 같은지 비교 (예: 데이터베이스의 Primary Key)
    // RecyclerView는 이 값으로 두 아이템이 '같은 객체'인지를 판단
    override fun areItemsTheSame(oldItem: Word, newItem: Word): Boolean {
        return oldItem.id == newItem.id
    }

    // 아이템의 내용(데이터)이 완전히 같은지 비교
    // areItemsTheSame이 true일 경우에만 호출됨
    // RecyclerView는 이 값으로 아이템의 '내용이 변경'되었는지를 판단
    override fun areContentsTheSame(oldItem: Word, newItem: Word): Boolean {
        return oldItem == newItem // Word가 data class이므로, '==' 비교는 모든 프로퍼티를 비교함
    }
}