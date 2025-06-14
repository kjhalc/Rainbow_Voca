// 파일 위치: utils/StudentAdapter.kt
package com.example.englishapp.utils

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.englishapp.R
import com.example.englishapp.databinding.ItemStudentBinding
import com.example.englishapp.model.StudyMemberProfile

class StudentAdapter(
    private val onItemClick: (StudyMemberProfile) -> Unit
) : ListAdapter<StudyMemberProfile, StudentAdapter.StudentViewHolder>(diffCallback) {

    /* ────────────── ViewHolder ────────────── */
    inner class StudentViewHolder(private val binding: ItemStudentBinding)
        : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onItemClick(getItem(p))
            }
        }

        fun bind(member: StudyMemberProfile) = with(binding) {
            /* ── 닉네임 ── */
            textMemberNameItem.text = member.nickname

            /* ── 출석 아이콘 ── */
            textAttendanceIconItem.text =
                if (member.isAttendedToday) "출" else "미"
            cardAttendanceIconBgItem.setCardBackgroundColor(
                Color.parseColor(member.getAttendanceColorCode())
            )

            /* ── 오답 아이콘 ── */
            when {
                !member.isAttendedToday -> {
                    textWrongAnswerCountItem.text = "오답\n-"
                    cardWrongAnswerIconBgItem.setCardBackgroundColor(Color.WHITE)
                    textWrongAnswerCountItem.setTextColor(Color.parseColor("#BDBDBD"))
                }
                member.wrongAnswerCount == 0 -> {
                    textWrongAnswerCountItem.text = "오답\n0"
                    cardWrongAnswerIconBgItem.setCardBackgroundColor(Color.WHITE)
                    textWrongAnswerCountItem.setTextColor(Color.parseColor("#4CAF50"))
                }
                else -> {
                    textWrongAnswerCountItem.text = "오답\n${member.wrongAnswerCount}"
                    cardWrongAnswerIconBgItem.setCardBackgroundColor(
                        Color.parseColor(member.getWrongAnswerColorCode())
                    )
                    textWrongAnswerCountItem.setTextColor(Color.WHITE)
                }
            }

            /* ── 진도율 ── */
            val progress = member.progressRate
            progressStudyRatioItem.progress = progress
            textStudyRatioItem.text = "$progress%"
            val barColor = when {
                progress >= 80 -> "#4CAF50"
                progress >= 50 -> "#FFC107"
                else           -> "#FF5252"
            }
            progressStudyRatioItem.progressTintList =
                ColorStateList.valueOf(Color.parseColor(barColor))

            // 메달 아이콘은 여기서 바꾸셈 -> 그냥 색깔만 다른 동그라미인 상태
            val medalRes = when (member.rankOrder) {
                1 -> R.drawable.ic_rank_gold
                2 -> R.drawable.ic_rank_silver
                3 -> R.drawable.ic_rank_bronze
                else -> null
            }
            binding.ivRankIcon.isVisible = medalRes != null            // ★ binding.ivRankIcon
            medalRes?.let { binding.ivRankIcon.setImageResource(it) }

            /* ── 프로필 사진 ── */
            Glide.with(root.context)
                .load(member.profileImage ?: R.drawable.ic_profile_default)
                .circleCrop()
                .placeholder(R.drawable.ic_profile_default)
                .error(R.drawable.ic_profile_default)
                .into(imageMemberProfileItem)
        }
    }

    /* ───────── Adapter override ───────── */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        StudentViewHolder(
            ItemStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))   // 메달 포함 모든 바인딩
    }

    /* ───────── helper / DiffUtil ───────── */
    fun getMemberAt(position: Int) = getItem(position)

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<StudyMemberProfile>() {
            override fun areItemsTheSame(o: StudyMemberProfile, n: StudyMemberProfile) =
                o.userId == n.userId
            override fun areContentsTheSame(o: StudyMemberProfile, n: StudyMemberProfile) = o == n
        }
    }
}
