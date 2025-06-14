package com.example.englishapp.ui // 실제 프로젝트의 패키지 경로로 수정해주세요

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.englishapp.R // R 클래스 임포트
import com.example.englishapp.datastore.TokenDataStore // TokenDataStore 경로
import com.example.englishapp.viewmodel.UserLoginViewModel // ViewModel
import kotlinx.coroutines.launch

// 액티비티 이름은 회원가입인데 실질적으로 로그인 화면
class SignInActivity : AppCompatActivity() {

    // UserLoginViewModel 인스턴스를 가져옵니다.
    private val viewModel: UserLoginViewModel by viewModels()

    // UI 요소 변수 선언
    private lateinit var editTextSignInEmail: EditText
    private lateinit var editTextSignInPassword: EditText
    private lateinit var buttonSignIn: Button
    private lateinit var progressBarSignIn: ProgressBar
    private lateinit var textViewGoToRegister: TextView // 가입 화면으로 이동하는 텍스트뷰

    companion object {
        private const val TAG = "SignInActivity" // 로그 태그
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // activity_sign_in.xml 레이아웃을 화면에 설정합니다.
        setContentView(R.layout.activity_sign_in)

        // UI 요소들을 ID를 통해 초기화합니다.
        editTextSignInEmail = findViewById(R.id.editTextSignInEmail)
        editTextSignInPassword = findViewById(R.id.editTextSignInPassword)
        buttonSignIn = findViewById(R.id.buttonSignIn)
        progressBarSignIn = findViewById(R.id.progressBarSignIn)
        textViewGoToRegister = findViewById(R.id.textViewGoToRegister)

        // ViewModel의 LiveData 변경을 감지하고 UI를 업데이트하는 Observer들을 설정합니다.
        setupObservers()

        // "로그인" 버튼 클릭 시 동작을 정의
        buttonSignIn.setOnClickListener {
            val email = editTextSignInEmail.text.toString().trim()
            val password = editTextSignInPassword.text.toString().trim()

            // 입력값 유효성 검사
            if (!validateInputs(email, password)) {
                return@setOnClickListener // 유효하지 않으면 리스너 종료
            }
            viewModel.clearError() // 새 요청 전 이전 에러 상태 초기화
            Log.d(TAG, "Sign In button clicked with email: $email")
            // ViewModel의 signInUser 함수를 호출하여 로그인을 시도
            viewModel.signInUser(email, password)
        }

        // "계정이 없으신가요? 가입하기" 텍스트 클릭 시 동작을 정의
        textViewGoToRegister.setOnClickListener {
            navigateToRegister() // 가입 화면(LoginActivity)으로 이동
        }
    }

    // ViewModel의 LiveData를 관찰하여 UI를 업데이트하는 함수
    // MVVM 패턴의 핵심 -> View는 ViewModel의 상태를 관찰하고 반응
    private fun setupObservers() {
        // 로딩 상태 LiveData 관찰
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d(TAG, "isLoading LiveData changed: $isLoading")
            // 로딩 중에는 프로그래스바 표시하고 버튼/입력필드 비활성화
            progressBarSignIn.visibility = if (isLoading) View.VISIBLE else View.GONE
            buttonSignIn.isEnabled = !isLoading
            editTextSignInEmail.isEnabled = !isLoading
            editTextSignInPassword.isEnabled = !isLoading
        }

        // 사용자 프로필 LiveData 관찰 (로그인 성공 시)
        viewModel.userProfile.observe(this) { userProfile ->
            Log.d(TAG, "userProfile LiveData changed: $userProfile")
            userProfile?.let { // userProfile이 null이 아니면 (로그인 성공)
                // (선택 사항) ID 토큰을 DataStore에 저장
                // DataStore에는 SharedPreferences의 대체제로, 코루틴 지원
                viewModel.idToken.value?.let { token ->
                    Log.d(TAG, "Saving token to DataStore: $token")
                    lifecycleScope.launch { // 코루틴으로 비동기 저장
                        TokenDataStore.saveToken(this@SignInActivity, token)
                    }
                }
                navigateToMain() // 메인 액티비티로 이동
            }
        }

        // 오류 메시지 LiveData 관찰
        viewModel.error.observe(this) { errorMessage ->
            Log.d(TAG, "error LiveData changed: $errorMessage")
            if (errorMessage != null) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                // viewModel.clearError() // 필요에 따라 여기서 오류를 바로 지울 수도 있습니다.
            }
        }
    }

    // 로그인 입력값 유효성을 검사하는 함수
    private fun validateInputs(email: String, pass: String): Boolean {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (pass.isBlank()) {
            Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        // 로그인 시에는 비밀번호 길이 검사를 생략 가능  (가입 시에는 6자 이상 등으로 강제)
        return true
    }

    // MainActivity로 화면을 전환하는 함수
    private fun navigateToMain() {
        Log.d(TAG, "Navigating to MainActivity.")
        val intent = Intent(this, MainActivity::class.java)
        // 플래그 설정 -> 새 태스크 생성 및 기존 태스크 클리어
        // 로그인 후에는 뒤로가기로 로그인 화면으로 못돌아감

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // SignInActivity 종료
    }

    // LoginActivity(가입 화면)로 화면을 전환하는 함
    private fun navigateToRegister() {
        Log.d(TAG, "Navigating to LoginActivity (for registration).")
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        // SignInActivity를 종료할지 여부는 UX 흐름에 따라 결정합니다.
        // 보통은 로그인 화면에서 가입 화면으로 갈 때 현재 화면을 종료하지않기에 finish는 주석 처리
        // finish()
    }
}