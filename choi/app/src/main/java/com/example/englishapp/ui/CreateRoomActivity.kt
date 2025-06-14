package com.example.englishapp.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.example.englishapp.R
import com.example.englishapp.utils.ApiResult
import com.example.englishapp.viewmodel.StudyRoomViewModel
import com.example.englishapp.viewmodel.StudyRoomViewModelFactory

/**
 * 새로운 스터디룸을 생성하는 화면의 Activity.
 */
class CreateRoomActivity : AppCompatActivity() {

    // private val viewModel: StudyRoomViewModel by viewModels() //

    // 스터디 뷰모델은 -> 인자를 Application, StudyRoomRespository, UserRepository 3개가 필요하니
    // 뷰모델 팩토리를 사용하여 생성ㅗ
    private val viewModel: StudyRoomViewModel by viewModels {
        StudyRoomViewModelFactory(application)
    }

    private lateinit var editName: EditText //
    private lateinit var editPw: EditText //
    private lateinit var btnCreate: Button //
    private lateinit var btnBackToList: AppCompatButton //
    private lateinit var progressBar: ProgressBar //

    /** Activity 초기화 및 UI 요소 설정, 이벤트 리스너를 설정하기. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_room) //

        editName = findViewById(R.id.edit_room_name) //
        editPw = findViewById(R.id.edit_room_password) //
        btnCreate = findViewById(R.id.btn_create_room) //
        btnBackToList = findViewById(R.id.btn_back_to_room_list) //
        progressBar = findViewById(R.id.progress_bar_create_room) //

        // ViewModel에 현재 사용자 정보 임시 초기화 (추후 로그인 시스템 연동 필요)
        // viewModel.initUser("레인", 101, "logo") //

        btnCreate.setOnClickListener {
            val title = editName.text.toString().trim() //
            val password = editPw.text.toString().trim() //

            if (title.isNotEmpty() && password.isNotEmpty()) { //
                // ViewModel의 createNewRoom 호출 (변경된 Repository 호출 방식과 호환)
                viewModel.createNewRoom(title, password) //
            } else {
                Toast.makeText(this, "제목과 비밀번호를 모두 입력하세요.", Toast.LENGTH_SHORT).show() //
            }
        }
        observeViewModel() //
    }

    /** ViewModel의 LiveData 변경을 관찰하여 UI를 업데이트하기. */
    private fun observeViewModel() {
        viewModel.createOp.observe(this, Observer { result ->
            when (result) {
                is ApiResult.Success -> { //
                    setResult(Activity.RESULT_OK) //
                    finish() //
                }
                is ApiResult.Error -> { //
                    // 오류 메시지는 viewModel.message LiveData를 통해 Toast로 표시됨
                }
                is ApiResult.Loading -> { /* 로딩 상태는 isLoading LiveData로 처리 */ } //
                is ApiResult.Idle -> { /* 초기 상태 */ } //
                null -> {}
            }
        })

        viewModel.isLoading.observe(this, Observer { isLoading ->
            progressBar.isVisible = isLoading //
            btnCreate.isEnabled = !isLoading //
            btnBackToList.isEnabled = !isLoading //
        })

        viewModel.message.observe(this, Observer { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show() //
                viewModel.clearMessage() //
            }
        })
    }

    /** '스터디룸 리스트로 돌아가기' 버튼 클릭 시 호출하기. */
    fun goToStudyRoomListScreen(view: View) { //
        if (viewModel.isLoading.value == false) { //
            finish() //
        } else {
            Toast.makeText(this, "작업 진행 중에는 돌아갈 수 없습니다.", Toast.LENGTH_SHORT).show() //
        }
    }
}