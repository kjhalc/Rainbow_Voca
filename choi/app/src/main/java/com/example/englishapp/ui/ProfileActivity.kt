package com.example.englishapp.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.englishapp.R
import com.example.englishapp.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private var selectedImageUri: Uri? = null

    // Firebase 서비스 인스턴스 초기화
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { Firebase.firestore }
    private val storage by lazy { Firebase.storage }

    // 갤러리에서 이미지를 선택한 결과를 처리하는 런처
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 선택된 이미지를 화면에 미리 보여주고, URI를 변수에 저장
            binding.imageProfile.setImageURI(it)
            selectedImageUri = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 화면이 처음 생성될 때, 현재 프로필 정보를 불러옵니다.
        loadUserProfile()

        // '이미지 변경' 텍스트 클릭 시 갤러리를 엽니다.
        binding.textChangeImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // '저장하기' 버튼 클릭 시 프로필 저장 프로세스를 시작합니다.
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    // 현재 사용자의 프로필 정보를 Firestore에서 불러와 UI에 적용하는 함수
    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return // 로그인 상태가 아니면 종료

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname")
                    val profileImageUrl = document.getString("profileImage")
                    binding.editTextNickname.tag = nickname

                    // 현재 닉네임을 EditText에 설정
                    binding.editTextNickname.setText(nickname)

                    // 현재 프로필 이미지를 Glide로 로드
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile_default) // 로딩 중 이미지
                            .error(R.drawable.ic_profile_default)     // 에러 시 이미지
                            .into(binding.imageProfile)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "프로필을 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    // 프로필 저장 로직
    private fun saveProfile() {
        val originalNickname = binding.editTextNickname.tag as? String ?: ""
        val newNickname = binding.editTextNickname.text.toString().trim()
        val imageUri = selectedImageUri
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (newNickname.isEmpty()) {
            Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 수정: showLoading 전에 체크
        if (originalNickname == newNickname && imageUri == null) {
            Toast.makeText(this, "변경된 내용이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        if (imageUri != null) {
            val fileName = "profile_images/${userId}_${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference.child(fileName)

            storageRef.putFile(imageUri)
                .addOnProgressListener { taskSnapshot ->
                    val progress =
                        (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    Log.d("ProfileActivity", "Upload progress: $progress%")
                }
                .addOnSuccessListener { taskSnapshot ->
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                        Log.d("ProfileActivity", "Image uploaded successfully: $downloadUri")
                        updateProfileInFirestore(userId, newNickname, downloadUri.toString())
                    }.addOnFailureListener { e ->
                        Log.e("ProfileActivity", "Failed to get download URL", e)
                        handleFailure("이미지 URL을 가져오는데 실패했습니다: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileActivity", "Failed to upload image", e)
                    handleFailure("이미지 업로드에 실패했습니다: ${e.message}")
                }
        } else {
            updateProfileInFirestore(userId, newNickname, null)
        }
    }

    // 🔥 추가: 에러 메시지 개선
    private fun updateProfileInFirestore(userId: String, nickname: String, imageUrl: String?) {
        val userUpdates = mutableMapOf<String, Any>()
        userUpdates["nickname"] = nickname

        if (imageUrl != null) {
            userUpdates["profileImage"] = imageUrl
            Log.d("ProfileActivity", "Updating profile with image: $imageUrl")
        }

        Log.d("ProfileActivity", "Updating Firestore with: $userUpdates")

        firestore.collection("users").document(userId)
            .update(userUpdates)
            .addOnSuccessListener {
                Log.d("ProfileActivity", "Firestore update successful")
                showLoading(false)
                Toast.makeText(this, "프로필이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                selectedImageUri = null
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("ProfileActivity", "Firestore update failed", e)
                handleFailure("프로필 정보 저장에 실패했습니다: ${e.message}")
            }
    }



    // 로딩 상태에 따라 UI를 변경하는 함수
    private fun showLoading(isLoading: Boolean) {
        binding.profileProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveProfile.isEnabled = !isLoading
        binding.textChangeImage.isEnabled = !isLoading
        binding.editTextNickname.isEnabled = !isLoading
    }

    // 실패 처리를 통합한 함수
    private fun handleFailure(message: String) {
        showLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}