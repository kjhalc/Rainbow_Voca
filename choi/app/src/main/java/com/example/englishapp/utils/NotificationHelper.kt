package com.example.englishapp.utils // 실제 프로젝트의 패키지명으로 수정

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
// import com.example.englishapp.R // 직접 사용하지 않으면 불필요



// 안드로이드 O (API 26) 이상 버전에서 알림을 표시하기 위해
// 필수적인 알림 채널을 생성하고 관리하는 유틸리티
// 서버에서 FCM을 통해 알림 페이로드를 보내더라도, 클라이언트에서 해당 알림을
// 사용자에게 보여주려면 이 채널 설정이 선행
object NotificationHelper {


    // 알림 채널의 고유 ID. 서버에서 특정 채널로 알림을 보내도록 지정할 수도 있지만,
    // 일반적으로 클라이언트에서 이 ID로 채널을 만들고, FCM 메시지를 받으면 이 채널을 사용해 알림을 띄움
    private const val CHANNEL_ID = "english_app_reminders_channel"

    // 사용자에게 보여지는 알림 채널의 이름
    private const val CHANNEL_NAME = "학습 알림"

    // 알림 채널에 대한 설명 (앱 설정 화면 등에 노출)
    private const val CHANNEL_DESCRIPTION = "단어 학습 및 복습 알림 채널입니다."

    fun createNotificationChannel(context: Context) {
        // 알림 채널 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // 알림의 중요도 설정 -> 일반적인 알림 수준
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            // 알림 채널 객체 생성 -> ID, 이름, 중요도를 인자로 받음
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }

            // 시스템의 NotificationManager 서비스 인스턴스를 가져옴
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 동일한 채널 ID로 중복 생성을 방지하기 위해 이미 채널이 존재하는지 확인
            // 채널 설정은 앱 설치 후 한번만 수행되거나, 설정이 변경될 때 업데이트
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(channel)
                Log.i("NotificationHelper", "Notification channel '$CHANNEL_NAME' (ID: $CHANNEL_ID) created.")
            } else {
                Log.d("NotificationHelper", "Notification channel '$CHANNEL_NAME' (ID: $CHANNEL_ID) already exists.")
            }
        }
    }
}