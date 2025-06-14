package com.example.englishapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.englishapp.R
import com.example.englishapp.model.StudyRoom
import com.example.englishapp.model.StudyRoomSearchResultItem
import com.example.englishapp.network.MyStudyRoomBasicInfo
import com.example.englishapp.utils.ApiResult
import com.example.englishapp.utils.StudyRoomAdapter
import com.example.englishapp.viewmodel.StudyRoomViewModel
import com.example.englishapp.viewmodel.StudyRoomViewModelFactory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore

/**
 * 스터디룸 목록을 보여주고, 방 생성 및 참여 기능을 제공하는 Activity.
 */
class StudyRoomActivity : AppCompatActivity() {

    // 기존 뷰모델 사용하는 모델은 아웃
    // private val viewModel: StudyRoomViewModel by viewModels()

    // 뷰모델 팩토리를 사용하는 구조로
    private val viewModel: StudyRoomViewModel by viewModels {
        StudyRoomViewModelFactory(application)
    }


    private lateinit var btnClose: ImageButton
    private lateinit var btnJoin: Button
    private lateinit var btnCreate: Button
    private lateinit var recyclerRooms: RecyclerView
    private lateinit var roomAdapter: StudyRoomAdapter
    private lateinit var progressBarLoading: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var textEmptyList: TextView

    private val createRoomLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadJoinedRooms()
        }
    }

    /** Activity 생성 및 초기 UI 설정을 수행하기. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        // --- 이 코드를 추가해주세요 ---
//        // 개발 모드(에뮬레이터 사용)일 때만 실행되도록 하는 것이 좋습니다.
//        // 여기서는 테스트를 위해 항상 실행하도록 설정합니다.
//        val useEmulators = false
//
//        if (useEmulators) {
//            // Firebase Auth 에뮬레이터 설정
//            try {
//                Firebase.auth.useEmulator("10.0.2.2", 9099) // Auth 에뮬레이터 포트
//            } catch (e: IllegalStateException) {
//                // 이미 설정된 경우 예외가 발생할 수 있으므로 무시
//            }
//
//            // Firebase Firestore 에뮬레이터 설정
//            try {
//                val firestore = Firebase.firestore
//                val settings = FirebaseFirestoreSettings.Builder()
//                    .setHost("10.0.2.2:8080") // Firestore 에뮬레이터 포트
//                    .setSslEnabled(false)
//                    .setPersistenceEnabled(false) // 에뮬레이터 사용 시 데이터 유지 비활성화
//                    .build()
//                firestore.firestoreSettings = settings
//            } catch (e: IllegalStateException) {
//                // 이미 설정된 경우 예외가 발생할 수 있으므로 무시
//            }
//        }
//        // --- 여기까지 추가 ---

        setContentView(R.layout.activity_study_room)

        // viewModel.initUser("레인", 101, "logo")

        btnClose = findViewById(R.id.btn_close)
        swipeRefresh = findViewById(R.id.swipe_refresh_layout_study_rooms)
        recyclerRooms = findViewById(R.id.recycler_study_rooms)
        btnJoin = findViewById(R.id.btn_join_room)
        btnCreate = findViewById(R.id.btn_create_room)
        progressBarLoading = findViewById(R.id.progress_bar_study_room)
        textEmptyList = findViewById(R.id.text_empty_study_rooms)

        setupRecyclerView()
        setupSwipeRefresh()
        setupClickListeners()
        observeViewModel()
    }

    /** RecyclerView 초기 설정을 수행하기. */
    private fun setupRecyclerView() {
        roomAdapter = StudyRoomAdapter(
            emptyList(),
            onItemClick = { room -> navigateToAdmin(room) },
            onItemLongClick = { room -> showLeaveDialog(room) }
        )
        recyclerRooms.layoutManager = LinearLayoutManager(this)
        recyclerRooms.adapter = roomAdapter
    }

    /** SwipeRefreshLayout 초기 설정을 수행하기. */
    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            viewModel.loadJoinedRooms()
        }
    }

    /** 클릭 리스너들을 설정. */
    private fun setupClickListeners() {
        btnClose.setOnClickListener { finish() }

        btnCreate.setOnClickListener {
            val intent = Intent(this, CreateRoomActivity::class.java)
            createRoomLauncher.launch(intent)
        }

        btnJoin.setOnClickListener { showSearchRoomDialog() }
    }

    /** ViewModel의 LiveData 변경을 관찰하여 UI를 업데이트하기. */
    private fun observeViewModel() {
        viewModel.joinedRooms.observe(this, Observer { result ->
            when (result) {
                is ApiResult.Success -> {
                    // 1. 서버가 보내준 '기본 정보 목록'(result.data)을 가져옵니다.
                    // basicInfoList는 List<MyStudyRoomBasicInfo> 타입입니다.
                    val basicInfoList: List<MyStudyRoomBasicInfo> = result.data

                    // 2. '기본 정보' 하나하나를 '상세 정보(StudyRoom)'로 변환하여 새 목록을 만듭니다.
                    // Kotlin의 .map 함수는 리스트의 각 아이템을 새로운 형태의 아이템으로 변환하여
                    // 새로운 리스트를 만들어주는 매우 유용한 기능입니다.
                    val studyRooms: List<StudyRoom> = basicInfoList.map { basicInfo ->
                        // basicInfo를 재료로 StudyRoom 객체를 새로 만듭니다.
                        StudyRoom(
                            title = basicInfo.title,
                            ownerNickname = basicInfo.ownerNickname,
                            ownerId = basicInfo.ownerId,
                            memberCount = basicInfo.memberCount,
                            // 이 목록 화면에서는 상세 정보가 필요 없으므로, 기본값이나 빈 값으로 채웁니다.
                            password = "",
                            members = mutableListOf(),
                            isAdminForCurrentUser = false // 이 정보가 필요하다면 userProfile과 비교해야 함
                        )
                    }

                    // 3. 이제 어댑터가 원하는 '상세 정보 목록(studyRooms)'을 전달합니다.
                    // 이제 타입이 일치하므로 오류가 발생하지 않습니다.
                    roomAdapter.updateRooms(studyRooms)

                    // 4. 나머지 코드는 변환된 studyRooms 목록을 사용합니다.
                    val isEmpty = studyRooms.isEmpty()
                    recyclerRooms.isVisible = !isEmpty
                    textEmptyList.isVisible = isEmpty
                    if (isEmpty) {
                        textEmptyList.text = "참여한 스터디룸이 없습니다.\n새로운 방에 참여하거나 직접 만들어보세요!"
                    }
                }
                is ApiResult.Error -> {
                    recyclerRooms.visibility = View.GONE
                    textEmptyList.visibility = View.VISIBLE
                    textEmptyList.text = "목록을 불러오는데 실패했습니다: ${result.message}"
                }
                is ApiResult.Loading -> {
                    recyclerRooms.visibility = View.GONE
                    textEmptyList.visibility = View.GONE
                }
                is ApiResult.Idle -> {
                    recyclerRooms.visibility = View.GONE
                    textEmptyList.visibility = View.VISIBLE
                    textEmptyList.text = "참여한 스터디룸이 없습니다."
                }
                null -> { /* null인 경우 처리 */ }
            }
        })

        viewModel.joinOp.observe(this, Observer { result ->
            if (result is ApiResult.Success || result is ApiResult.Error) {
                viewModel.clearJoinOp()
            }
        })

        viewModel.leaveOp.observe(this, Observer { result ->
            if (result is ApiResult.Success || result is ApiResult.Error) {
                viewModel.clearLeaveOp()
            }
        })

        viewModel.isLoading.observe(this, Observer { isLoading ->
            if (swipeRefresh.isRefreshing && !isLoading) {
                swipeRefresh.isRefreshing = false
            }
            progressBarLoading.isVisible = isLoading && !swipeRefresh.isRefreshing

            if (isLoading && !swipeRefresh.isRefreshing) {
                recyclerRooms.visibility = View.GONE
                textEmptyList.visibility = View.GONE
            }
        })

        viewModel.message.observe(this, Observer { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearMessage()
            }
        })
    }

    /** 스터디룸 검색 다이얼로그를 표시. */
    private fun showSearchRoomDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_join_room, null)
        val searchViewDialog = dialogView.findViewById<SearchView>(R.id.dialog_search_view)
        val recyclerResultsDialog = dialogView.findViewById<RecyclerView>(R.id.dialog_recycler_search_results)
        val progressSearchDialog = dialogView.findViewById<ProgressBar>(R.id.dialog_search_progress_bar)
        var alertDialog: AlertDialog? = null

        val searchResultsAdapter = StudyRoomAdapter(
            emptyList(),
            onItemClick = { room ->
                showPasswordDialog(room)
                alertDialog?.dismiss()
            }
        )
        recyclerResultsDialog.layoutManager = LinearLayoutManager(this)
        recyclerResultsDialog.adapter = searchResultsAdapter

        viewModel.foundRooms.observe(this, Observer { result ->
            when (result) {
                is ApiResult.Loading -> progressSearchDialog.isVisible = true
                is ApiResult.Success -> {
                    progressSearchDialog.isVisible = false
                    val roomsForDialog = result.data.map { searchItem ->
                        StudyRoom(
                            title = searchItem.title,
                            password = "",
                            ownerNickname = searchItem.ownerNickname,
                            ownerId = 0,
                            memberCount = searchItem.memberCount,
                            isAdminForCurrentUser = false
                        )
                    }
                    searchResultsAdapter.updateRooms(roomsForDialog)
                }
                is ApiResult.Error -> {
                    progressSearchDialog.isVisible = false
                    Toast.makeText(this, "검색 오류: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                is ApiResult.Idle -> progressSearchDialog.isVisible = false
                null -> { /* null 처리 */ }
            }
        })

        alertDialog = AlertDialog.Builder(this)
            .setTitle("스터디룸 검색")
            .setView(dialogView)
            .setNegativeButton("취소") { _, _ -> viewModel.clearFoundRooms() }
            .create()

        searchViewDialog.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { if (it.trim().isNotEmpty()) viewModel.findRooms(it.trim()) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    searchResultsAdapter.updateRooms(emptyList())
                    viewModel.clearFoundRooms()
                }
                return true
            }
        })
        alertDialog.show()
    }

    /** 비밀번호 입력 다이얼로그를 표시하기. */
    private fun showPasswordDialog(roomToJoin: StudyRoom) {
        val editPassword = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "비밀번호"
        }

        AlertDialog.Builder(this)
            .setTitle("'${roomToJoin.title}' 방 참여")
            .setView(editPassword)
            .setPositiveButton("참여") { dialog, _ ->
                val passwordInput = editPassword.text.toString()
                if (passwordInput.isNotEmpty()) {
                    // 디버깅용 로그 추가
                    android.util.Log.d("StudyRoom", "=== 방 참여 시도 ===")
                    android.util.Log.d("StudyRoom", "Room title: ${roomToJoin.title}")
                    android.util.Log.d("StudyRoom", "Password entered: ${if (passwordInput.isNotEmpty()) "Yes" else "No"}")

                    viewModel.joinExistingRoom(roomToJoin.title, passwordInput)
                } else {
                    Toast.makeText(this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 스터디룸 나가기 확인 다이얼로그를 표시. */
    private fun showLeaveDialog(room: StudyRoom) {
        AlertDialog.Builder(this)
            .setTitle("'${room.title}' 방에서 나가기")
            .setMessage("정말로 이 방에서 나가시겠습니까?")
            .setPositiveButton("나가기") { dialog, _ ->
                viewModel.leaveCurrentRoom(room.title)
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 관리자 페이지(AdminActivity)로 이동. */
    private fun navigateToAdmin(room: StudyRoom) {
        val intent = Intent(this, AdminActivity::class.java).apply {
            putExtra("room_title", room.title)
        }
        startActivity(intent)
    }
}