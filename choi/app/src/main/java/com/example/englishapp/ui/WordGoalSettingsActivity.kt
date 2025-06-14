package com.example.englishapp.ui // 실제 프로젝트 패키지 경로로 수정해주세요

import android.app.Activity
import android.content.Intent
import android.graphics.Color // 직접 색상 변경 시 필요할 수 있음 (여기서는 isSelected 활용)
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
// import android.widget.Toast // 필요시 Toast 메시지용
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.englishapp.R // R 클래스 임포트 (실제 R 파일 경로에 맞춰주세요)

// 백엔드 관점: 이 Activity는 사용자가 '오늘의 학습 단어 개수'라는 특정 설정을 변경하는 UI를 제공합니다.
// 여기서 선택된 값(예: 10, 20, 30 등)은 이전 화면(MainActivity)으로 전달되어,
// 해당 사용자의 프로필 정보(Firestore의 UserProfile 문서 내 'dailyWordGoal' 필드 또는 로컬 SharedPreferences)에
// 최종적으로 저장됩니다. 이 저장된 값은 '오늘의 학습' 기능 실행 시 학습할 단어의 수를 결정하는
// 파라미터로 사용되어, 간접적으로 서버 데이터 조회 로직에 영향을 미칩니다.
class WordGoalSettingsActivity : AppCompatActivity() {

    // UI 요소들을 클래스 멤버 변수로 선언하여 여러 메소드에서 접근 용이하게 함
    private lateinit var btnClose: ImageButton
    private lateinit var btn10: AppCompatButton
    private lateinit var btn20: AppCompatButton
    private lateinit var btn30: AppCompatButton
    private lateinit var btn40: AppCompatButton
    private lateinit var btn50: AppCompatButton

    // 버튼과 해당 목표값을 매핑하여 관리
    private lateinit var goalButtons: Map<Int, AppCompatButton>

    companion object {
        const val TAG = "WordGoalSettingsAct"
        // 결과를 반환할 때 사용할 Intent Extra 키. MainActivity에서도 이 키를 사용하여 값을 추출합니다.
        // 백엔드 관점: API 요청/응답에서 필드명을 일관되게 사용하는 것과 유사하게,
        // Activity 간 데이터 전달 시에도 키 값을 명확히 정의하고 공유하는 것이 중요합니다.
        const val EXTRA_SELECTED_GOAL = "NEW_DAILY_GOAL"
        // MainActivity에서 현재 설정된 목표를 받아올 때 사용할 키
        const val EXTRA_CURRENT_GOAL = "CURRENT_DAILY_GOAL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "단어 설정" XML 레이아웃(activity_word_goal_settings.xml)을 화면에 표시합니다.
        // XML 파일명이 실제와 다른 경우 R.layout.실제_파일명 으로 수정해주세요.
        setContentView(R.layout.activity_word_goal_settings) // ⬅️ 보내주신 XML 파일명으로 설정

        Log.d(TAG, "onCreate: WordGoalSettingsActivity 시작됨")

        initializeViews() // UI 요소 초기화
        setupClickListeners() // 클릭 리스너 설정

        // MainActivity로부터 전달받은 현재 학습 목표값 확인 및 UI에 반영
        val currentGoal = intent.getIntExtra(EXTRA_CURRENT_GOAL, 10) // 기본값 10개 또는 적절한 기본값
        Log.d(TAG, "Activity 생성. 전달받은 현재 학습 목표 (EXTRA_CURRENT_GOAL): $currentGoal")
        highlightCurrentGoalButton(currentGoal)
    }

    /**
     * XML 레이아웃의 UI 요소들을 찾아 멤버 변수에 할당하고, 버튼들을 Map으로 그룹화합니다.
     */
    private fun initializeViews() {
        btnClose = findViewById(R.id.btn_close)
        btn10 = findViewById(R.id.btn_10)
        btn20 = findViewById(R.id.btn_20)
        btn30 = findViewById(R.id.btn_30)
        btn40 = findViewById(R.id.btn_40)
        btn50 = findViewById(R.id.btn_50)

        // 일단 테스트를 위해 1~5개로 설정
        goalButtons = mapOf(
            1 to btn10,
            2 to btn20,
            3 to btn30,
            4 to btn40,
            5 to btn50
        )
    }

    /**
     * 각 버튼에 대한 클릭 리스너를 설정합니다.
     */
    private fun setupClickListeners() {
        // 닫기 버튼 클릭 리스너: 설정 변경 없이 현재 Activity를 종료합니다.
        btnClose.setOnClickListener {
            Log.d(TAG, "닫기 버튼 클릭. 설정 변경 없이 Activity.RESULT_CANCELED 설정 후 종료.")
            setResult(Activity.RESULT_CANCELED) // 작업이 취소되었음을 이전 Activity에 알립니다.
            finish() // 현재 Activity 종료
        }

        // 각 단어 개수 설정 버튼 클릭 리스너 설정
        // 각 버튼 클릭은 특정 설정값(10, 20 등)을 선택하는 사용자 액션입니다.
        // 이 값으로 서버(Firestore) 또는 로컬 저장소에 사용자별 설정으로 기록됩니다.
        goalButtons.forEach { (goalValue, button) ->
            button.setOnClickListener {
                setSelectedGoalAndFinish(goalValue)
            }
        }
    }

    /**
     * 선택된 학습 목표 개수를 결과 Intent에 담아 이전 Activity(MainActivity)로 전달하고 현재 Activity를 종료합니다.
     * @param goal 선택된 단어 개수 (10, 20, 30, 40, 50 중 하나)
     */
    private fun setSelectedGoalAndFinish(goal: Int) {
        Log.i(TAG, "학습 목표 $goal 개 선택됨. 결과를 MainActivity로 전달하고 Activity를 종료합니다.")
        val resultIntent = Intent()
        // EXTRA_SELECTED_GOAL 키를 사용하여 선택된 목표 개수(goal)를 Intent에 추가합니다.
        resultIntent.putExtra(EXTRA_SELECTED_GOAL, goal)
        // 작업이 성공적으로 완료되었음을 나타내는 Activity.RESULT_OK와 함께 결과 Intent를 설정합니다.
        setResult(Activity.RESULT_OK, resultIntent)
        finish() // 현재 Activity 종료
    }

    /**
     * 현재 설정된 목표에 해당하는 버튼을 시각적으로 다르게 표시(선택 상태로 변경)합니다.
     * 이 기능을 제대로 활용하려면 각 버튼의 배경 드로어블이 android:state_selected 상태를 처리하는
     * <selector>로 정의되어 있어야 합니다. (예: res/drawable/selector_goal_button_background.xml)
     * @param currentGoal 현재 설정된 학습 목표 단어 수
     */
    private fun highlightCurrentGoalButton(currentGoal: Int) {
        goalButtons.forEach { (goalValue, button) ->
            // 현재 목표값과 버튼의 목표값이 일치하면 true, 아니면 false로 isSelected 상태 설정
            button.isSelected = (goalValue == currentGoal)
        }
        if (goalButtons.containsKey(currentGoal)) {
            Log.d(TAG, "현재 학습 목표 $currentGoal 에 해당하는 버튼이 선택 상태로 표시됨.")
        } else {
            Log.w(TAG, "전달받은 현재 학습 목표 $currentGoal 에 해당하는 버튼이 없어 선택 표시 불가.")
        }
    }
}