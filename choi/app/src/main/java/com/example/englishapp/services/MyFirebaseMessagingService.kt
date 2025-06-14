package com.example.englishapp.services // 실제 프로젝트의 패키지명으로 수정

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.englishapp.R // R 파일 경로 확인 필요
import com.example.englishapp.ui.MainActivity // 알림 클릭 시 이동할 Activity
import com.example.englishapp.utils.NotificationHelper // NotificationHelper 사용
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


// 서비스는 클라이언트 앱에서 FCM 메시지를 수신하고 처리하는 핵심 엔트리 포인트
// 서버(예: Firebase Functions, 자체 Admin 서버 등)에서 FCM을 통해 푸시 알림을 보내면
// 이 서비스의 onMessageReceived() 또는 onNewToken() 메서드가 호출
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val NOTIFICATION_ID_CUMULATIVE_REVIEW = 1
        private const val NOTIFICATION_ID_TEN_MIN_REVIEW = 2
    }
    /**
     * FCM 메시지 수신 시 호출됩니다.
     * 앱이 포그라운드에 있을 때: 이 메소드가 항상 호출되어 메시지를 처리합니다.
     * 앱이 백그라운드/종료 상태일 때:
     * - "알림 메시지(notification payload)"만 있는 경우: 시스템 트레이에 자동으로 알림이 표시되고,
     * 사용자가 알림을 탭하여 앱을 열 때 실행될 인텐트에 데이터가 담길 수 있습니다. 이 메소드는 직접 호출되지 않을 수 있습니다.
     * - "데이터 메시지(data payload)"만 있거나 둘 다 있는 경우: 이 메소드가 호출되어 데이터를 처리하고
     * 필요시 직접 로컬 알림을 생성할 수 있습니다.
     */

//    서버에서 FCM 메시지를 보낼 때, "notification" 페이로드와 "data" 페이로드를 선택하거나 조합
//   "notification": FCM이 자동으로 시스템 트레이 알림을 처리 (앱이 백그라운드일 때). 간단한 정보 전달에 용이
//    "data": 앱이 직접 메시지 내용을 처리. 앱 상태에 따른 맞춤형 동작(예: UI 업데이트, 로컬 알림 커스터마이징, 백그라운드 동기화) 가능

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // FCM 메시지 고유 ID 및 발신자(FCM 프로젝트 번호) 로깅
        Log.d(TAG, "FCM Message Id: ${remoteMessage.messageId}")
        Log.d(TAG, "FCM From: ${remoteMessage.from}")

        // 데이터 페이로드가 있는지 확인
        // 서버에서 "data" 필드에 key-value 형태로 추가 정보를 보낸 경우
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "FCM Message data payload: " + remoteMessage.data)
            // 데이터 메시지에 따른 추가 작업 수행 가능 (예: 특정 데이터 동기화)
        }

        // 알림 페이로드가 있는지 확인 (Cloud Functions에서 notification 객체로 보낸 경우)
        // 서버에서 "notification" 필드에 title, body 등을 담아 보내면, 이 부분이 메시지 내용을 구성
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: getString(R.string.app_name) // 알림 제목 (없으면 앱 이름)
            val body = notification.body ?: "새로운 알림이 도착했습니다."   // 알림 본문
            Log.i(TAG, "Received FCM Notification: Title='$title', Body='$body'")

            // NotificationHelper를 사용하여 사용자에게 알림 표시
            // 백엔드 관점: 서버에서 보낸 알림 내용을 바탕으로 클라이언트에서 실제 사용자에게 보여줄 알림을 생성
            // 여기서 로컬 알림을 직접 생성하면, 앱이 포그라운드에 있을 때도 일관된 알림
            sendNotification(applicationContext, title, body)
        }
    }

    /**
     * FCM 등록 토큰이 갱신될 때 호출됩니다.
     * 새 토큰은 서버(Firestore)에 업데이트해야 합니다.
     * FCM 토큰은 특정 앱 인스턴스(기기)를 식별하는 고유 값
     * 서버는 이 토큰을 알아야 해당 기기로 푸시 메시지를 보낼 수 있음
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Refreshed FCM token: $token")
        // 갱신된 토큰을 서버(여기서는 Firestore)에 전송하여 저장
        sendRegistrationToServer(token)
    }

    /**
     * 수신된 FCM 메시지를 바탕으로 사용자에게 로컬 알림을 생성하고 표시
     * 이 함수는 클라이언트 앱이 사용자에게 알림을 시각적으로 제시하는 역할
     * 알림의 내용(title, messageBody)은 서버에서 전달된 값을 사용
     */
    private fun sendNotification(context: Context, title: String, messageBody: String) {
        // 알림 채널 생성 (Android 8.0 이상 필수, 매번 호출해도 괜찮음)
        NotificationHelper.createNotificationChannel(context)

        // 알림 클릭 시 MainActivity를 열기 위한 Intent 생성
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // 필요시 MainActivity에 특정 액션이나 데이터를 전달하기 위해 putExtra 사용 가능
            // 예: remoteMessage.data.forEach { intent.putExtra(it.key, it.value) }
        }

        // PendingIntent 플래그 설정
        // Android 12(S, API 31) 이상에서는 FLAG_IMMUTABLE 또는 FLAG_MUTABLE 중 하나를 명시적으로 설정해야함
        // FLAG_IMMUTABLE: 생성된 PendingIntent의 내용(Intent 등)을 변경할 수 없도록 함. 보안상 권장
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // FLAG_IMMUTABLE은 API 23부터 사용 가능
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // 알림을 탭했을 때 실행될 작업을 나타내는 PendingIntent 생성
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0 /* Request code - 각 알림을 구분하려면 고유하게 설정 */,
            intent,
            pendingIntentFlag
        )

        // 알림 채널 ID (NotificationHelper에서 생성한 채널과 동일해야 함)
        val channelId = "english_app_reminders_channel" // NotificationHelper와 동일한 채널 ID 사용


        // drawble 에 logo 넣어놨으니 해결 부탁..
        // 알림 빌더를 사용하여 알림 구성
        // 알림 로고는 여기서 건드릴 것! -> 지금 잘 안되는중
        // 스몰이 내리기 전에 뜨는거
        // 라지가 내린 후에 옆에 보이는 거
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            //  Asset Studio로 만든 새 실루엣 아이콘으로 교체 -> 이것도 잘안됨
            .setSmallIcon(R.drawable.ic_notification_rainbow)
            .setColor(ContextCompat.getColor(context, R.color.primary))  // 아이콘 틴트 색상
            .setLargeIcon(
                BitmapFactory.decodeResource(  // 컬러 로고는 여기에 그대로 둡니다.
                    context.resources,
                    R.drawable.rainbow_voca_logo
                ))
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // NotificationManager 시스템 서비스 가져오기
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


        val notificationId = when {
            title.contains("누적 복습") -> NOTIFICATION_ID_CUMULATIVE_REVIEW
            title.contains("10분 후 복습") -> NOTIFICATION_ID_TEN_MIN_REVIEW
            else -> System.currentTimeMillis().toInt() // 기타 알림은 기존 방식 사용
        }
        // 각 알림에 고유한 ID를 부여 (System.currentTimeMillis()는 간단한 예시, 중복될 수 있으므로 주의)
        // 알림 유형별로 ID를 다르게 하거나, 내용 기반으로 ID 생성 고려
        // -> 당장은 2개지만 추후 다른 알림 관리에 필요
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Notification sent to system tray: Title='$title', ID='$notificationId'")
    }

    /**
     * 새 토큰 또는 갱신된 토큰을 Firestore에 저장하는 함수
     * 백엔드 관점: 클라이언트 앱 인스턴스의 FCM 토큰을 서버(여기서는 Firestore)에 저장
     * 나중에 이 토큰을 사용하여 특정 사용자/기기에 메시지를 보낼 수 있도록 함
     * 일반적으로 사용자 로그인 시 또는 토큰 갱신 시 호출
     */
    private fun sendRegistrationToServer(token: String?) {
        token?.let {
            val userId = Firebase.auth.currentUser?.uid

            if (userId != null && userId != "UNKNOWN_USER") {
                Log.i(TAG, "Sending FCM token to Firestore for user $userId.")

                val userDocRef = Firebase.firestore.collection("users").document(userId)

                // ✅ update 대신 set with merge 사용
                userDocRef.set(
                    mapOf("fcmToken" to it),
                    SetOptions.merge()  // 기존 데이터 유지하면서 fcmToken만 추가/업데이트
                )
                    .addOnSuccessListener {
                        Log.i(TAG, "FCM token successfully saved in Firestore for user $userId.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error saving FCM token in Firestore for user $userId", e)

                        // ✅ 실패 시 재시도 로직 추가
                        userDocRef.update("fcmToken", it)
                            .addOnFailureListener { updateError ->
                                Log.e(TAG, "Both set and update failed", updateError)
                            }
                    }
            } else {
                Log.d(TAG, "User not logged in, cannot save FCM token yet.")
            }
        }
    }
}