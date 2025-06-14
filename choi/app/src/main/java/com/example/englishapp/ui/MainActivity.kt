// --- [생략 없는 진짜 최종 완성본] ui/MainActivity.kt ---
package com.example.englishapp.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.example.englishapp.R
import com.example.englishapp.databinding.ActivityMainBinding
import com.example.englishapp.viewmodel.LearningButtonState
import com.example.englishapp.viewmodel.MainPageViewModel
import com.example.englishapp.viewmodel.MainPageViewModelFactory
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.startActivity
import com.bumptech.glide.Glide
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainPageViewModel by viewModels {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "UNKNOWN_USER"
        MainPageViewModelFactory(application, userId)
    }

    private lateinit var learningLauncher: ActivityResultLauncher<Intent>
    private lateinit var reviewLauncher: ActivityResultLauncher<Intent>
    private lateinit var wordGoalSettingsLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.userId == "UNKNOWN_USER") {
            Toast.makeText(this, "로그인 정보가 유효하지 않습니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }

        initializeLaunchers()
        setupUI()
        setupObservers()
        checkNotificationPermission()

        // FCM 토큰 초기화 -> 알림용
        initializeFCM()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 드로어가 열려 있으면 닫습니다.
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // 드로어가 닫혀 있으면, 콜백을 비활성화하고
                    // 기본 뒤로가기 동작(액티비티 종료 등)을 실행합니다.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        // 위에서 만든 새로운 뒤로 가기 콜백을 액티비티에 등록합니다.
        onBackPressedDispatcher.addCallback(this, callback)

    }


    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
        setupClickListeners()
    }

    private fun initializeLaunchers() {
        val commonResultHandler = { resultCode: Int ->
            if (resultCode == Activity.RESULT_OK) {
                Log.d(
                    TAG,
                    "Activity finished with RESULT_OK. Main page will update automatically via listener."
                )
            }
        }
        learningLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                commonResultHandler(result.resultCode)
            }
        reviewLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                commonResultHandler(result.resultCode)
            }
        wordGoalSettingsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    // WordGoalSettingsActivity가 보낸 새로운 목표 값을 꺼냅니다.
                    val newGoal =
                        result.data?.getIntExtra(WordGoalSettingsActivity.EXTRA_SELECTED_GOAL, 10)
                            ?: 10
                    Log.d(TAG, "새로운 학습 목표 $newGoal 개를 받았습니다. 저장을 요청합니다.")

                    // ViewModel에게 새로운 목표를 Firestore에 저장하라고 요청합니다.
                    viewModel.updateDailyGoal(newGoal)
                }
            }
    }

    private fun setupClickListeners() {
        binding.btnLearning.setOnClickListener {
            val currentState = viewModel.learningButtonState.value
            when (currentState) {
                LearningButtonState.READY_TO_START_LEARNING -> {
                    val intent = Intent(this, WordStudyActivity::class.java).apply {
                        putExtra(
                            WordStudyActivity.DAILY_GOAL_EXTRA,
                            viewModel.mainPageData.value?.dailyWordGoal ?: 10
                        )
                    }
                    learningLauncher.launch(intent)
                }

                LearningButtonState.TEN_MIN_REVIEW_AVAILABLE -> {
                    val intent = Intent(this, WordQuizActivity::class.java).apply {
                        putExtra(WordQuizActivity.QUIZ_TYPE_EXTRA, "10min")
                        putExtra(WordQuizActivity.USER_ID_EXTRA, viewModel.userId)
                    }
                    reviewLauncher.launch(intent)
                }

                else -> {}
            }
        }
        binding.btnReview.setOnClickListener {
            if ((viewModel.mainPageData.value?.todayReviewCount ?: 0) > 0) {
                val intent = Intent(this, WordQuizActivity::class.java).apply {
                    putExtra(WordQuizActivity.QUIZ_TYPE_EXTRA, "cumulative")
                    putExtra(WordQuizActivity.USER_ID_EXTRA, viewModel.userId)
                }
                reviewLauncher.launch(intent)
            } else {
                Toast.makeText(this, "오늘 누적 복습할 단어가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.mainPageData.observe(this) { data ->
            if (data == null) return@observe
            updateNavigationHeader(data.nickname, data.email)
            binding.todayReviewCount.text = "${data.todayReviewCount}개"
            binding.todayLearningGoal.text = "${data.dailyWordGoal}개"
            binding.expectedFinishDate.text = data.estimatedCompletionDate
            binding.monthDiligence.text = "${data.progressRate.toInt()}%"
            binding.btnReview.isEnabled = data.todayReviewCount > 0
            binding.progressBarRedVertical.progress = (data.stageCounts["red"] ?: 0L).toInt()
            binding.progressBarOrangeVertical.progress = (data.stageCounts["orange"] ?: 0L).toInt()
            binding.progressBarYellowVertical.progress = (data.stageCounts["yellow"] ?: 0L).toInt()
            binding.progressBarGreenVertical.progress = (data.stageCounts["green"] ?: 0L).toInt()
            binding.progressBarBlueVertical.progress = (data.stageCounts["blue"] ?: 0L).toInt()
            binding.progressBarNavyVertical.progress = (data.stageCounts["indigo"] ?: 0L).toInt()
            binding.progressBarPurpleVertical.progress = (data.stageCounts["violet"] ?: 0L).toInt()
        }
        viewModel.learningButtonState.observe(this) { state ->
            val button = binding.btnLearning
            when (state) {
                LearningButtonState.READY_TO_START_LEARNING -> {
                    button.text = "오늘의 학습"; button.isEnabled = true
                }

                LearningButtonState.TEN_MIN_REVIEW_AVAILABLE -> {
                    button.text = "10분 후 복습"; button.isEnabled = true
                }

                LearningButtonState.LEARNING_COMPLETED -> {
                    button.text = "학습 완료"; button.isEnabled = false
                }

                else -> {
                    button.text = "로딩 중..."; button.isEnabled = false
                }
            }
        }
        viewModel.errorMessage.observe(this) { error ->
            error?.let { Toast.makeText(this, "오류: $it", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_all_words -> startActivity(Intent(this, AllWordsActivity::class.java))
            R.id.nav_setting_word -> {
                val intent = Intent(this, WordGoalSettingsActivity::class.java).apply {
                    putExtra(
                        WordGoalSettingsActivity.EXTRA_CURRENT_GOAL,
                        viewModel.mainPageData.value?.dailyWordGoal ?: 10
                    )
                }
                wordGoalSettingsLauncher.launch(intent)
            }

            R.id.nav_studyRoom -> startActivity(Intent(this, StudyRoomActivity::class.java))
            R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun updateNavigationHeader(nickname: String?, email: String?) {
        val headerView = binding.navView.getHeaderView(0)
        val navUsername = headerView.findViewById<TextView>(R.id.nav_header_nickname)
        val navUserEmail = headerView.findViewById<TextView>(R.id.nav_header_email)
        val navProfileImage = headerView.findViewById<ImageView>(R.id.nav_header_imageView) // 추가

        navUsername?.text = nickname ?: "사용자"
        navUserEmail?.text = email ?: "이메일 정보 없음"

        // 프로필 이미지 업데이트 추가
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            Firebase.firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    val profileImageUrl = document.getString("profileImage")
                    if (!profileImageUrl.isNullOrEmpty() && navProfileImage != null) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile_default)
                            .error(R.drawable.ic_profile_default)
                            .circleCrop()
                            .into(navProfileImage)
                    }
                }
        }
    }

//    @Deprecated("Deprecated in Java")
//    override fun onBackPressed() {
//        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
//            binding.drawerLayout.closeDrawer(GravityCompat.START)
//        } else {
//            super.onBackPressed()
//        }
//    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "알림을 받으려면 설정에서 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToLogin() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun initializeFCM() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token: $token")

            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null && userId != "UNKNOWN_USER") {
                Firebase.firestore
                    .collection("users")
                    .document(userId)
                    .set(mapOf("fcmToken" to token), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM 토큰 저장 완료")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "FCM 토큰 저장 실패", e)
                    }
            }
        }
    }
}