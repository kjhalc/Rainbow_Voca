package com.example.englishapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishapp.model.UserProfile // UserProfile에 email 필드가 추가되었는지 확인
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
// import com.google.firebase.firestore.SetOptions // 새 사용자 등록 시에는 .set()만으로도 충분할 수 있음
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// 사용자 로그인 및 가입 관련 비즈니스 로직을 처리하는 ViewModel 클래스
// ViewModel을 사용하여 Activity의 화면 회전 등에도 데이터가 유지되도록 함
class UserLoginViewModel : ViewModel() {

    // Firebase Authentication, Firestore(db) 인스턴스를 가져옴
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // LiveData 객체들 : UI에서 관찰하며 데이터 변경에 따라 UI 업데이트
    // _변수명 == 내부에서만 수정 가능한 MutableLiveData (private)
    // 변수명 == 외부에서는 읽기만 가능한 LiveData (public)

    // 현재 로그인된 사용자의 프로필 정보(Firestore에서 가져옴)
    private val _userProfile = MutableLiveData<UserProfile?>()
    val userProfile: LiveData<UserProfile?> = _userProfile

    // ID 토큰, 자체 백엔드 API와 통신시 인증 수단으로 사용 가능
    private val _idToken = MutableLiveData<String?>()
    val idToken: LiveData<String?> = _idToken

    // 오류메시지 담는 변수 -> UI에 오류 표시용
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // 로딩상태(네트워크 요청 등 비동기 작업 진행 여부)를 담는 LiveData
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // 로그 태그 및 Firestore 컬렉션을 상수로
    companion object {
        private const val TAG = "UserLoginViewModel"
        private const val USERS_COLLECTION = "users"
    }

    // 앱 시작 시 호출, Firebase에 로그인된 사용자가 있는지 확인(자동로그인 기능)
    // LoginActivity의 onCreate에서 호출됨
    fun checkCurrentUserSession() {
        _isLoading.value = true
        val firebaseUser: FirebaseUser? = auth.currentUser // 현재 Firebase Auth에 로그인된 사용자 확인
        if (firebaseUser != null) { // 로그인된 사용자가 있으면
            Log.d(TAG, "Current user found: ${firebaseUser.uid}, Email: ${firebaseUser.email}")
            fetchUserProfileFromFirestore(firebaseUser) // Firestore에서 프로필 가져오기
            firebaseUser.getIdToken(true).addOnCompleteListener { task -> // true로 강제 갱신 (선택 사항)
                if (task.isSuccessful) {
                    _idToken.value = task.result?.token // LiveData 업데이트
                    Log.d(TAG, "ID Token fetched/refreshed successfully.")
                } else {
                    Log.w(TAG, "ID Token fetch failed: ", task.exception)
                }
                // 로딩잠깐 하고 메인으로 바로 이동
                // 자동 로그인 시 isLoading은 fetchUserProfileFromFirestore 내부에서 false로 설정됨
            }
        } else { //로그인 된 사용자가 없으면 자동 로그인 X
            // 로딩상태 종료하고 로그인화면에 남음
            Log.d(TAG, "No current user.")
            _userProfile.value = null
            _idToken.value = null
            _isLoading.value = false
        }
    }
    // Firestore에서 특정 FirebaseUser의 프로필 정보를 가져오는 내부 함수
    // isSignInAttempt 플래그는 로그인 시도 직후 프로필을 가져올 떄와 일반적인 경우를 구분
    private fun fetchUserProfileFromFirestore(firebaseUser: FirebaseUser, isSignInAttempt: Boolean = false) {
        // isLoading은 호출하는 쪽에서 관리하거나, 여기서 시작/종료를 명확히 할 수 있음
        // _isLoading.value = true // 여기서 true로 설정하면 checkCurrentUserSession의 초기 isLoading과 중복될 수 있음

        // Firestore의 users 컬렉션에서 UID를 문서 ID로 사용하여 사용자 정보 조회
        db.collection(USERS_COLLECTION).document(firebaseUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // 문서가 존재하면 UserProfile 객체로 변환
                    val user = document.toObject(UserProfile::class.java)
                    _userProfile.value = user // LiveData 업데이트 -> UI가 이를 감지하여 메인 화면으로 이동
                    Log.d(TAG, "User profile fetched from Firestore: $user")
                } else {
                    // 로그인 시도(isSignInAttempt=true) 시 프로필이 없다면 오류로 간주 가능
                    // 가입 직후라면 Firestore 쓰기가 지연될 아주 짧은 가능성도 있으나, await 사용 시 거의 없음.
                    if (isSignInAttempt) {
                        Log.w(TAG, "User profile not found in Firestore for UID: ${firebaseUser.uid} after sign-in attempt.")
                        _error.value = "사용자 프로필 정보를 찾을 수 없습니다."
                    } else {
                        // 자동 로그인 시 프로필이 없는 경우 (매우 드문 케이스, 가입이 완전히 안된 경우)
                        Log.w(TAG, "User profile not found for already authenticated user: ${firebaseUser.uid}")
                        _error.value = "저장된 사용자 프로필이 없습니다. 다시 로그인해주세요."
                    }
                    _userProfile.value = null
                }
                _isLoading.value = false // 여기서 로딩 상태 종료
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting user profile from Firestore: ", exception)
                _error.value = "프로필 로딩 실패: ${exception.message}"
                _userProfile.value = null
                _isLoading.value = false // 여기서 로딩 상태 종료
            }
    }


    // 이메일, 비밀번호, 닉네임으로 사용자 가입 (새로운 함수)
    // LoginActivity(실제로는 회원가입 화면)에서 호출
    fun registerUser(email: String, password: String, nickname: String) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch { // 코루틴 스코프 사용 - ViewModel이 clear될 때 자동 취소됨
            try {
                // 1. (선택 사항이지만 권장) Firestore에서 닉네임 중복 체크
                val nicknameQuery = db.collection(USERS_COLLECTION)
                    .whereEqualTo("nickname", nickname.lowercase()) // 소문자로 통일하여 중복 체크
                    .limit(1)
                    .get()
                    .await() // 코루틴 suspend 함수로 비동기를 동기처럼 처리

                if (!nicknameQuery.isEmpty) {
                    _error.value = "이미 사용 중인 닉네임입니다."
                    _isLoading.value = false
                    return@launch // 코루틴 종료
                }
                Log.d(TAG, "Nickname '$nickname' is available.")

                // 2. Firebase에 이메일/비밀번호로 사용자 생성
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user // 생성된 Firebase 사용자 객체

                if (firebaseUser != null) { // 사용자 생성이 성공적으로 완료된 경우
                    Log.d(TAG, "Firebase Auth user created successfully. UID: ${firebaseUser.uid}")

                    // UserProfile 객체 생성 -> 추후 관리자 모드 구현하며 추가 필요
                    val userProfileData = UserProfile(
                        uid = firebaseUser.uid,
                        nickname = nickname,
                        email = firebaseUser.email ?: email, // FirebaseUser에서 가져오거나 입력값 사용
                        dailyWordGoal = 10, // 기본 학습 목표 개수 -> 설정에서 바꿀 예정
                        hasStudiedToday = false,
                        createdAt = Timestamp.now() // 현재 시간으로 생성일 -> 관리에 용이
                    )

                    // 3. Firestore의 "users" 컬렉션에 사용자 프로필 정보 저장
                    // 문서 ID는 Firebase Auth UID와 동일하게 설정
                    db.collection(USERS_COLLECTION).document(firebaseUser.uid)
                        .set(userProfileData) // 새 사용자이므로 .set() 사용
                        .await()
                    Log.d(TAG, "User profile saved to Firestore for UID: ${firebaseUser.uid}")

                    // 4. LiveData 업데이트 -> UI 변경
                    _userProfile.value = userProfileData // 이때 메인화면으로 이동
                    val tokenResult = firebaseUser.getIdToken(false).await()
                    _idToken.value = tokenResult.token
                    Log.d(TAG, "ID Token fetched for new user.")

                } else { // 드문 경우지만 일단 에러 방어적 코드
                    _error.value = "Firebase 사용자 생성에 실패했습니다." // 이 경우는 거의 발생하지 않음
                    Log.w(TAG, "createUserWithEmailAndPassword success but firebaseUser is null")
                }
            } catch (e: Exception) { // 가입 과정 중 예외 발생 시
                Log.e(TAG, "Error during registration: ", e)
                when (e) {
                    is FirebaseAuthUserCollisionException -> { // 중복오류
                        _error.value = "이미 가입된 이메일 주소입니다."
                    }

                    // 이 2개의 예외처리는 액티비티에서 수정 가능
                    is FirebaseAuthWeakPasswordException -> { //password type 에러(액티비티에서 수정가능)
                        _error.value = "비밀번호는 6자 이상이어야 합니다."
                    }
                    is FirebaseAuthInvalidCredentialsException -> { //input type 에러(액티비티에서 수정가능)
                        _error.value = "이메일 형식이 올바르지 않습니다."
                    }
                    else -> {
                        _error.value = "가입 중 오류 발생: ${e.localizedMessage}"
                    }
                } // 오류시 정보 및 토큰 초기화 하고 로딩 상태 종료시킴
                _userProfile.value = null
                _idToken.value = null
            } finally {
                _isLoading.value = false // 성공/실패 관계없이 로딩 종료
            }
        }
    }

    // 이메일, 비밀번호로 기존 사용자를 로그인시키는 함수
    // SignInActivity(로그인 화면)에서 호출
    fun signInUser(email: String, password: String) {
        _isLoading.value = true // 로딩 상태 시작
        _error.value = null // 이전 오류 메시지 초기화
        viewModelScope.launch {
            try {
                // Firebase Authenication 이메일/비밀번호로 로그인 요청
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user // 로그인 성공한 객체

                if (firebaseUser != null) {
                    Log.d(TAG, "Sign-in successful. UID: ${firebaseUser.uid}, Email: ${firebaseUser.email}")
                    // 로그인 성공 후 Firestore에서 사용자 프로필 정보 가져오기
                    fetchUserProfileFromFirestore(firebaseUser, isSignInAttempt = true)
                    // ID 토큰도 가져오기
                    val tokenResult = firebaseUser.getIdToken(false).await()
                    _idToken.value = tokenResult.token
                    Log.d(TAG, "ID Token fetched for signed-in user.")
                } else {
                    // 이 경우는 signInWithEmailAndPassword 성공 후 user가 null인 드문 경우
                    _error.value = "로그인 후 사용자 정보를 가져오지 못했습니다."
                    Log.w(TAG, "signInWithEmailAndPassword success but firebaseUser is null")
                    _isLoading.value = false // 로딩 종료
                }
            } catch (e: Exception) { // 로그인 과정 중 예외 발생
                Log.e(TAG, "Error during sign-in: ", e)
                // FirebaseAuthInvalidUserException (사용자 없음), FirebaseAuthInvalidCredentialsException (비번 틀림) 등
                _error.value = "로그인 실패: 이메일 또는 비밀번호를 확인해주세요."
                _userProfile.value = null
                _idToken.value = null
                _isLoading.value = false // 로딩 종료
            }
            // fetchUserProfileFromFirestore 내부에서 _isLoading.value = false 처리를 하므로 여기서는 중복으로 안해도 됨
            // 단, signInWithEmailAndPassword 자체가 실패했을 때를 위해 finally는 유지하거나, 각 catch 블록에서 false 처리
        }
    }


    // 기존 registerNickname 함수는 이제 사용하지 않으므로 삭제하거나 주석 처리합니다.
    // fun registerNickname(nickname: String) { ... }


    // ViewModel의 오류 메시지를 초기화하는 함수
    // 새로운 작업 시작 전에 호출하여 이전 오류 메시지가 남아있지 않도록 함
    fun clearError() {
        _error.value = null
    }

    // 로그아웃 기능 (주석 해제 및 사용 가능) -> 당장은 안씀
    fun signOut() {
        // 로그 아웃 및 viewModel의 사용자 정보 초기화, ID 토큰 초기화
        auth.signOut()
        _userProfile.value = null
        _idToken.value = null
        Log.d(TAG, "User signed out.")
        // TokenDataStore에서도 토큰 삭제 필요하다면 추가
    }
}