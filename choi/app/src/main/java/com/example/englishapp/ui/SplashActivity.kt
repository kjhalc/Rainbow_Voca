/*
package com.example.englishapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast // ViewModel 오류 표시는 Snackbar 또는 Toast 선택 가능
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.englishapp.R // activity_splash 레이아웃 참조용
import com.example.englishapp.datastore.TokenDataStore
import com.example.englishapp.viewmodel.UserLoginViewModel
import kotlinx.coroutines.launch

// 앱 시작 시 로딩 및 사용자 인증 상태 확인
@SuppressLint("CustomSplashScreen") // 기본 스플래시 테마 사용 시 Lint 경고 무시
class SplashActivity : AppCompatActivity() {

    private val userLoginViewModel: UserLoginViewModel by viewModels()
    private val splashDisplayLengthMs: Long = 1500 // 스플래시 화면 최소 표시 시간 (1.5초)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash) // 스플래시 화면 레이아웃

        // ViewModel 관찰 설정 (토큰 유효성 검사 결과 수신)
        observeAuthenticationState()

        // 일정 시간 후 또는 토큰 확인 후 화면 전환
        Handler(Looper.getMainLooper()).postDelayed({
            checkTokenAndNavigate()
        }, splashDisplayLengthMs)
    }

    // 저장된 토큰 확인 및 다음 화면으로 이동 결정
    private fun checkTokenAndNavigate() {
        lifecycleScope.launch {
            val savedToken = TokenDataStore.getToken(applicationContext)

            if (savedToken.isNullOrBlank()) {
                // 저장된 토큰 없음: 로그인 화면으로 이동
                navigateToLogin()
            } else {
                // 저장된 토큰 있음: 자동 로그인 시도 (토큰 유효성 검증)
                userLoginViewModel.attemptAutoLogin(savedToken)
                // 결과는 observeAuthenticationState() 에서 처리
            }
        }
    }

    // UserLoginViewModel의 LiveData 관찰
    private fun observeAuthenticationState() {
        // 자동 로그인 성공 시 (토큰 유효)
        userLoginViewModel.token.observe(this) { token ->
            if (token != null && !isFinishing && !isDestroyed) { // Activity가 유효한 상태일 때만 실행
                // 현재 LiveData는 토큰 값 자체를 발행하므로,
                // attemptAutoLogin 성공 시 token 값이 null이 아니면 성공으로 간주
                navigateToMain()
            }
            // 토큰이 null로 바뀌는 경우는 로그아웃 또는 명시적 초기화 시.
            // attemptAutoLogin 실패 시에는 error LiveData가 사용됨.
        }

        // 자동 로그인 실패 또는 기타 오류 발생 시
        userLoginViewModel.error.observe(this) { errorMessage ->
            if (errorMessage != null && !isFinishing && !isDestroyed) {
                // 자동 로그인 실패 (예: 토큰 만료)
                Toast.makeText(this, "자동 로그인 실패: $errorMessage", Toast.LENGTH_LONG).show()
                userLoginViewModel.consumeError() // 오류 메시지 소비

                // 저장된 토큰 삭제 후 로그인 화면으로 이동
                lifecycleScope.launch {
                    TokenDataStore.clearToken(applicationContext)
                    navigateToLogin()
                }
            }
        }
    }

    // LoginActivity로 이동
    private fun navigateToLogin() {
        if (!isFinishing && !isDestroyed) { // Activity가 유효한 상태일 때만 실행
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish() // SplashActivity 종료
        }
    }

    // MainActivity로 이동
    private fun navigateToMain() {
        if (!isFinishing && !isDestroyed) { // Activity가 유효한 상태일 때만 실행
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish() // SplashActivity 종료
        }
    }
}*/
