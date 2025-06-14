package com.example.englishapp.ui // 실제 프로젝트의 패키지 경로로 수정해주세요

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.englishapp.R // R 클래스 임포트
import com.example.englishapp.datastore.TokenDataStore // TokenDataStore 경로
import com.example.englishapp.viewmodel.UserLoginViewModel // ViewModel
import kotlinx.coroutines.launch

// 액티비티 이름은 로그인인데 실질적으로 회원가입 화면

class LoginActivity : AppCompatActivity() { // 클래스 이름을 RegisterActivity로 변경하는 것도 고려해보세요.

    // ViewModel 인스턴스 - SignInActivity와 동일한 UserLoginViewModel 사용
    private val viewModel: UserLoginViewModel by viewModels()

    // UI 요소 변수 선언
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextNickname: EditText // 회원가입에만 있는 닉네임 필드
    private lateinit var buttonRegister: Button
    private lateinit var buttonGoToSignIn: Button // "이미 계정이 있는 경우 로그인" 버튼 (이전 buttonLogin의 역할 변경)
    private lateinit var progressBarLogin: ProgressBar // XML에 해당 ID의 ProgressBar가 있어야 함

    companion object {
        private const val TAG = "LoginActivity" // 또는 RegisterActivityTAG
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 가입용 XML 레이아웃을 설정합니다. (이메일, 비번, 닉네임 필드와 가입 버튼, 로그인 화면 이동 버튼이 있는 XML)
        setContentView(R.layout.activity_login) // 이 XML은 이전에 단순화해서 드린 버전 기준입니다.

        // UI 요소 초기화 (activity_login.xml의 ID와 일치해야 함)
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextNickname = findViewById(R.id.editTextNickname)
        buttonRegister = findViewById(R.id.buttonRegister) // "이메일로 가입하기" 버튼
        buttonGoToSignIn = findViewById(R.id.buttonLogin)   // "이미 계정이 있는 경우 로그인" 버튼 (ID는 buttonLogin 그대로 사용)
        progressBarLogin = findViewById(R.id.progress_bar_login) // XML에 이 ID가 있어야 합니다.

        // ViewModel LiveData 관찰 설정
        setupObservers()

        // 현재 사용자 세션 확인 (자동 로그인 시도)
        // 이 화면이 앱의 첫 화면이라면 자동 로그인을 여기서 처리
        // 이미 로그인된 사용자가 있으면 바로 MainActivity로 이동
        viewModel.checkCurrentUserSession()

        // "가입하기" 버튼 클릭 리스너 설정
        buttonRegister.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val nickname = editTextNickname.text.toString().trim()

            if (!validateInputs(email, password, nickname)) { // 가입 시에는 모든 항목 유효성 검사
                return@setOnClickListener
            }
            viewModel.clearError()
            Log.d(TAG, "Register button clicked with email: $email, nickname: $nickname")
            viewModel.registerUser(email, password, nickname) // ViewModel의 가입 함수 호출
        }

        // "이미 계정이 있는 경우 로그인" 버튼 클릭 리스너 설정 -> SignInActivity로 이동
        buttonGoToSignIn.setOnClickListener {
            Log.d(TAG, "Navigate to SignInActivity button clicked.")
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            // 현재 가입 화면을 종료할지 여부는 UX에 따라 결정
            // finish()
        }
    }

    // ViewModel의 LiveData Observer 설정
    // SignInActivity와 거의 동일한 구조

    private fun setupObservers() {
        // 로딩상태
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d(TAG, "isLoading LiveData changed: $isLoading")
            progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
            buttonRegister.isEnabled = !isLoading
            buttonGoToSignIn.isEnabled = !isLoading // 이 버튼도 로딩 중에는 비활성화
            editTextEmail.isEnabled = !isLoading
            editTextPassword.isEnabled = !isLoading
            editTextNickname.isEnabled = !isLoading
        }

        // userProfile LiveData 관찰 (가입 성공 또는 자동 로그인 성공 시)
        viewModel.userProfile.observe(this) { userProfile ->
            Log.d(TAG, "userProfile LiveData changed: $userProfile")
            userProfile?.let {
                // 가입 성공 또는 자동 로그인 성공
                viewModel.idToken.value?.let { token ->
                    Log.d(TAG, "Saving token to DataStore: $token")
                    lifecycleScope.launch {
                        TokenDataStore.saveToken(this@LoginActivity, token)
                    }
                }
                navigateToMain() // 메인 액티비티로 이동
            }
        }

        // 에러메시지 관찰
        viewModel.error.observe(this) { errorMessage ->
            Log.d(TAG, "error LiveData changed: $errorMessage")
            if (errorMessage != null) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                // viewModel.clearError() // 필요시 여기서 호출
            }
        }
    }

    // 가입 입력값 유효성 검사 함수
    // 로그인과 달리 닉네임 검사 및 비밀번호 길이 검사
    private fun validateInputs(email: String, pass: String, nickname: String): Boolean {
        // 각 정책들은 알맞게 수정 가능
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (pass.isBlank() || pass.length < 6) {
            Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (nickname.isBlank()) {
            Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (nickname.length < 2 || nickname.length > 10) { // 닉네임 길이 정책 예시
            Toast.makeText(this, "닉네임은 2자 이상 10자 이하로 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun navigateToMain() {
        Log.d(TAG, "Navigating to MainActivity.")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // LoginActivity 종료
    }
}