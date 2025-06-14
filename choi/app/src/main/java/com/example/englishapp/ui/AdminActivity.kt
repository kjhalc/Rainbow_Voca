// 파일 위치: ui/AdminActivity.kt
package com.example.englishapp.ui

import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.englishapp.R
import com.example.englishapp.data.repository.UserProfile
import com.example.englishapp.databinding.ActivityAdminBinding
import com.example.englishapp.model.StudyMemberProfile
import com.example.englishapp.model.StudyRoom
import com.example.englishapp.utils.ApiResult
import com.example.englishapp.utils.StudentAdapter
import com.example.englishapp.viewmodel.AdminViewModel
import com.example.englishapp.viewmodel.AdminViewModelFactory
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val viewModel: AdminViewModel by viewModels {
        AdminViewModelFactory(application)
    }
    private lateinit var memberAdapter: StudentAdapter
    private var currentUserProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomTitle = intent.getStringExtra("room_title")
        if (roomTitle == null) {
            Toast.makeText(this, "방 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar(roomTitle)
        setupRecyclerView()
        observeViewModel()

        binding.buttonCloseAdminPage.setOnClickListener { finish() }
    }

    private fun setupToolbar(title: String) {
        binding.toolbarAdmin.title = title
        setSupportActionBar(binding.toolbarAdmin)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_admin, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_notify_all -> {
                val roomTitle = (viewModel.roomDetails.value as? ApiResult.Success)?.data?.title
                if (roomTitle != null) {
                    viewModel.sendBatchStudyReminder(roomTitle)
                } else {
                    Toast.makeText(this, "방 정보가 로드되지 않았습니다.", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        memberAdapter = StudentAdapter { member ->
            showMemberOptionsDialog(member)
        }
        binding.recyclerAdminMemberList.apply {
            layoutManager = LinearLayoutManager(this@AdminActivity)
            adapter = memberAdapter
        }
        attachSwipeToKick()
    }

    private fun observeViewModel() {
        viewModel.userProfile.observe(this) { profile ->
            currentUserProfile = profile

            if (profile != null && viewModel.roomDetails.value == null) {
                val roomTitle = intent.getStringExtra("room_title")
                if (roomTitle != null) {
                    // [수정] 일회성 로드 대신, 실시간 리스너를 시작합니다.
                    viewModel.startListeningToRoom(roomTitle)
                }
            }
        }

        viewModel.roomDetails.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> binding.progressBar.visibility = View.VISIBLE
                is ApiResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    updateUi(result.data)
                    // 새로운 리스트 정렬해서 전달
                    val sorted = result.data.members
                        .sortedByDescending { it.progressRate }  // 진도율 내림차순
                    memberAdapter.submitList(sorted)
                    // 멤버 유무에 따른 UI 처리
                    binding.textAdminEmptyMembers.visibility = if (result.data.members.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerAdminMemberList.visibility = if (result.data.members.isEmpty()) View.GONE else View.VISIBLE
                }
                is ApiResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "오류: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                else -> binding.progressBar.visibility = View.GONE
            }
        }

        viewModel.message.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        // [수정] 강퇴 등 작업 성공 시, 더 이상 수동으로 데이터를 새로고침하지 않습니다.
        // Firestore의 변경이 리스너를 통해 자동으로 감지되어 UI가 업데이트됩니다.
        viewModel.operationResult.observe(this) { result ->
            // 필요하다면 여기서 성공/실패에 대한 추가적인 UI 처리 가능
        }
    }

    private fun updateUi(room: StudyRoom) {
        binding.textAdminRoomTitleToolbar.text = room.title
        binding.textAdminNicknameBanner.text = room.ownerNickname

        val ownerProfile = room.members.find { it.userId == room.ownerId }
        Glide.with(this)
            .load(ownerProfile?.profileImage ?: R.drawable.ic_profile_default)
            .circleCrop()
            .into(binding.imageAdminProfileBanner)
    }

    private fun showMemberOptionsDialog(member: StudyMemberProfile) {
        val room = (viewModel.roomDetails.value as? ApiResult.Success)?.data ?: return
        val currentUser = currentUserProfile ?: return

        if (member.userId == currentUser.numericId) return
        if (room.ownerId != currentUser.numericId) {
            Toast.makeText(this, "방장만 사용할 수 있는 기능입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        if (!member.isAttendedToday) {
            options.add("📚 학습 독촉하기")
        }
        if (member.userId != room.ownerId) {
            options.add("🚫 강퇴하기")
        }

        if (options.isEmpty()) {
            Toast.makeText(this, "적용할 수 있는 동작이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(member.nickname)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "📚 학습 독촉하기" -> viewModel.sendStudyReminder(room.title, member.userId)
                    "🚫 강퇴하기" -> showKickConfirmDialog(room.title, member)
                }
            }.show()
    }

    private fun showKickConfirmDialog(roomTitle: String, member: StudyMemberProfile) {
        AlertDialog.Builder(this)
            .setTitle("${member.nickname}님을 강퇴시키겠습니까?")
            .setMessage("이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("확인") { _, _ -> viewModel.kickMember(roomTitle, member.userId) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun attachSwipeToKick() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val member = memberAdapter.getMemberAt(position)
                    val roomTitle = (viewModel.roomDetails.value as? ApiResult.Success)?.data?.title

                    if (member != null && roomTitle != null) {
                        showKickConfirmDialog(roomTitle, member)
                    }
                }
            }

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val room = (viewModel.roomDetails.value as? ApiResult.Success)?.data ?: return 0
                val member = memberAdapter.getMemberAt(viewHolder.adapterPosition) ?: return 0
                val currentUser = currentUserProfile ?: return 0
                return if (room.ownerId == currentUser.numericId && member.userId != room.ownerId) {
                    ItemTouchHelper.LEFT
                } else {
                    0
                }
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    .addSwipeLeftBackgroundColor(Color.parseColor("#F44336"))
                    .addSwipeLeftActionIcon(R.drawable.ic_delete_sweep)
                    .addSwipeLeftLabel("강퇴")
                    .setSwipeLeftLabelColor(Color.WHITE)
                    .create()
                    .decorate()
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerAdminMemberList)
    }
}