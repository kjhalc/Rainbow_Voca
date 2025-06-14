package com.example.englishapp.utils

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.englishapp.R
import com.example.englishapp.model.StudyRoom // StudyRoom 모델 import

class StudyRoomAdapter(
    private var studyRoomList: List<StudyRoom>, // StudyRoom 리스트 사용
    private val onItemClick: (StudyRoom) -> Unit, // StudyRoom 객체 전달
    private val onItemLongClick: ((StudyRoom) -> Unit)? = null // StudyRoom 객체 전달
) : RecyclerView.Adapter<StudyRoomAdapter.StudyRoomViewHolder>() {

    inner class StudyRoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.textView_room_title)
        private val ownerTextView: TextView = itemView.findViewById(R.id.textView_room_owner)
        private val memberCountTextView: TextView = itemView.findViewById(R.id.textView_member_count)

        fun bind(room: StudyRoom) { // StudyRoom 객체로 바인딩
            titleTextView.text = room.title
            ownerTextView.text = "방장: ${room.ownerNickname}"
            memberCountTextView.text = "인원: ${room.memberCount}"

            itemView.setOnClickListener { onItemClick(room) }
            onItemLongClick?.let { longClickCallback ->
                itemView.setOnLongClickListener {
                    longClickCallback(room)
                    true
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudyRoomViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_study_room, parent, false)
        return StudyRoomViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: StudyRoomViewHolder, position: Int) {
        holder.bind(studyRoomList[position])
    }

    override fun getItemCount(): Int = studyRoomList.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateRooms(newRooms: List<StudyRoom>) { // StudyRoom 리스트로 업데이트
        studyRoomList = newRooms
        notifyDataSetChanged()
    }
}