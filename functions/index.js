// --- [백엔드 최종 완성본 2] index.js ---

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();
const db = admin.firestore();

// 새로 만든 '계산기' 모듈과, 기존의 다른 모듈들을 가져옵니다.
const mainPageModule = require('./mainPageModule');
const studyRoomApi = require('./studyRoomApi');
const studyRoomTriggers = require('./studyRoomTriggers');


// ---------------------------------------------------------------------------------
// 🔥 메인 페이지 실시간 업데이트를 위한 자동 실행 함수 (Triggers)
// ---------------------------------------------------------------------------------



exports.onUserCreate = functions.region("asia-northeast3")
  .firestore.document("users/{userId}")
  .onCreate(async (snap, context) => {
    const userId = context.params.userId;
    console.log(`새로운 사용자 생성 감지: ${userId}. 초기 데이터를 설정합니다.`);
    // mainPageModule을 호출하여 계산 및 저장을 한 번에 처리합니다.
    return mainPageModule.updateMainPageDataForUser(userId);
  });



exports.onReviewDataChange = functions.region("asia-northeast3")
  .firestore.document("users/{userId}/review_words/{wordId}")
  .onWrite(async (change, context) => {
    const userId = context.params.userId;
    // 데이터가 변경되면, 계산 모듈을 호출하여 대시보드 전체를 업데이트합니다.
    return mainPageModule.updateMainPageDataForUser(userId);
  });


// 예약 함수 -> 자정에 모든 메인 페이지 데이터 계산
exports.updateAllUsersDataAtMidnight = functions.region("asia-northeast3")
  .pubsub.schedule("1 0 * * *") // 매일 00:01 실행 (시간 유지)
  .timeZone("Asia/Seoul")
  .onRun(async (context) => {
    console.log("매일 자정 스케줄러 실행: 모든 사용자 데이터 업데이트 시작");
    const usersSnapshot = await admin.firestore().collection("users").get();
    
    const tasks = usersSnapshot.docs.map(userDoc => {
      // ✨ 알림 보내는 로직은 삭제하고, 데이터 업데이트만 호출합니다.
      return mainPageModule.updateMainPageDataForUser(userDoc.id);
    });
    
    return Promise.all(tasks);
  });

  // 매일 오전 9시에 "누적 복습 알림 보내기"
  exports.sendDailyReviewReminders = functions.region("asia-northeast3")
  .pubsub.schedule("1 9 * * *") // 매일 09:01 실행 (시간 변경)
  .timeZone("Asia/Seoul")
  .onRun(async (context) => {
    console.log("매일 오전 9시 스케줄러 실행: 누적 복습 알림 발송 시작");
    const usersSnapshot = await admin.firestore().collection("users").get();

    const tasks = usersSnapshot.docs.map(userDoc => {
      const userData = userDoc.data();
      const userId = userDoc.id;
      
      // ✨ 데이터를 재계산하지 않고, 이미 저장된 값을 읽어서 알림만 보냅니다.
      if (userData && userData.todayReviewCount > 0 && userData.fcmToken) {
        const message = {
          token: userData.fcmToken,
          notification: {
            title: '🌈 좋은 아침! 복습으로 하루를 시작해봐요',
            body: `복습할 단어가 ${userData.todayReviewCount}개 있습니다. 잊어버리기 전에 확인하세요!`
          },
        };
        return admin.messaging().send(message);
      }
      return null;
    });
    
    return Promise.all(tasks);
  });


// 10분후 복습 알림 
exports.onLearningCompleteSendReminder = functions.region("asia-northeast3")
    .firestore.document("users/{userId}")
    .onUpdate(async (change, context) => {
        const newData = change.after.data();
        const oldData = change.before.data();
        const userId = context.params.userId;

         console.log(`[트리거] userId: ${userId}, 이전: ${oldData.isTodayLearningComplete}, 현재: ${newData.isTodayLearningComplete}`);

        // isTodayLearningComplete가 false에서 true로 바뀐 순간에만 동작
        if (oldData.isTodayLearningComplete === false && newData.isTodayLearningComplete === true) {
            console.log(`'오늘의 학습' 완료 감지: ${userId}, 즉시 복습 알림 및 상태 변경을 실행합니다.`);
            


            
            const fcmToken = newData.fcmToken;

            // 1. isPostLearningReviewReady를 true로 바꾸는 DB 업데이트 준비
            const dbUpdatePromise = db.collection("users").doc(userId).update({
                isPostLearningReviewReady: true
            });

            // 2. 푸시 알림 메시지 준비
            const notificationPromise = fcmToken ? admin.messaging().send({
                token: fcmToken,
                notification: {
                    title: '⏰ 10분후 복습 시간!',
                    body: '방금 학습한 단어들을 10분후 복습하면 더 오래 기억에 남아요!'
                }
            }) : Promise.resolve(); // 토큰이 없으면 아무것도 하지 않음

            // 3. 두 작업을 동시에 실행하여 안정성을 높임
            try {
                await Promise.all([dbUpdatePromise, notificationPromise]);
                console.log(`즉시 복습 준비 및 알림 발송 완료: ${userId}`);
            } catch (error) {
                console.error(`즉시 복습 준비/알림 중 오류 발생: ${userId}`, error);
            }
        }
    });



// 1. 누적 복습 알림 함수
// exports.sendCumulativeCountNotification = functions
//   .region('asia-northeast3')
//   .https.onCall(async (data, context) => {
//     try {
//       // 인증 확인
//       if (!context.auth) {
//         throw new functions.https.HttpsError(
//           'unauthenticated',
//           '인증되지 않은 사용자입니다.'
//         );
//       }

//       const userId = context.auth.uid;
//       const count = data.count || 0;

//       console.log(`누적 복습 알림 요청: userId=${userId}, count=${count}`);

//       // Firestore에서 사용자의 FCM 토큰 가져오기
//       const userDoc = await db.collection('users').doc(userId).get();
      
//       if (!userDoc.exists) {
//         throw new functions.https.HttpsError(
//           'not-found',
//           '사용자 정보를 찾을 수 없습니다.'
//         );
//       }

//       const fcmToken = userDoc.data().fcmToken;
      
//       if (!fcmToken) {
//         console.log(`사용자 ${userId}의 FCM 토큰이 없습니다.`);
//         return { success: false, message: 'FCM 토큰이 없습니다.' };
//       }

//       // FCM 메시지 구성
//       const message = {
//         token: fcmToken,
//         notification: {
//           title: '🌈 오늘의 누적 복습!',
//           body: `누적 복습할 단어가 ${count}개 있습니다. 지금 바로 확인해보세요!`
//         },
//         data: {
//           type: 'cumulative_review',
//           count: count.toString()
//         },
//         android: {
//           priority: 'high',
//           notification: {
//             channelId: 'english_app_reminders_channel'
//           }
//         }
//       };

//       // FCM 메시지 전송
//       const response = await admin.messaging().send(message);
//       console.log(`FCM 메시지 전송 성공: ${response}`);

//       return { success: true, messageId: response };

//     } catch (error) {
//       console.error('sendCumulativeCountNotification 오류:', error);
//       throw new functions.https.HttpsError(
//         'internal',
//         '알림 전송 중 오류가 발생했습니다.',
//         error
//       );
//     }
//   });

// 2. 10분 후 복습 알림 함수
// exports.sendImmediateTenMinReminder = functions
//   .region('asia-northeast3')
//   .https.onCall(async (data, context) => {
//     try {
//       // 인증 확인
//       if (!context.auth) {
//         throw new functions.https.HttpsError(
//           'unauthenticated',
//           '인증되지 않은 사용자입니다.'
//         );
//       }

//       const userId = context.auth.uid;
//       console.log(`10분 후 복습 알림 요청: userId=${userId}`);

//       // Firestore에서 사용자의 FCM 토큰 가져오기
//       const userDoc = await db.collection('users').doc(userId).get();
      
//       if (!userDoc.exists) {
//         throw new functions.https.HttpsError(
//           'not-found',
//           '사용자 정보를 찾을 수 없습니다.'
//         );
//       }

//       const fcmToken = userDoc.data().fcmToken;
      
//       if (!fcmToken) {
//         console.log(`사용자 ${userId}의 FCM 토큰이 없습니다.`);
//         return { success: false, message: 'FCM 토큰이 없습니다.' };
//       }

//       // FCM 메시지 구성
//       const message = {
//         token: fcmToken,
//         notification: {
//           title: '⏰ 10분 후 복습이 활성화 되었어요!',
//           body: '방금 학습한 단어들을 복습할 시간입니다. 지금 복습하면 효과가 2배!'
//         },
//         data: {
//           type: 'ten_min_review'
//         },
//         android: {
//           priority: 'high',
//           notification: {
//             channelId: 'english_app_reminders_channel'
//           }
//         }
//       };

//       // FCM 메시지 전송
//       const response = await admin.messaging().send(message);
//       console.log(`FCM 메시지 전송 성공: ${response}`);

//       return { success: true, messageId: response };

//     } catch (error) {
//       console.error('sendImmediateTenMinReminder 오류:', error);
//       throw new functions.https.HttpsError(
//         'internal',
//         '알림 전송 중 오류가 발생했습니다.',
//         error
//       );
//     }
//   });

// 3. 테스트용 함수 -> 딱히 안씀
exports.testNotification = functions
  .region('asia-northeast3')
  .https.onRequest(async (req, res) => {
    try {
      const { userId, title, body } = req.query;
      
      if (!userId) {
        res.status(400).send('userId가 필요합니다.');
        return;
      }

      const userDoc = await db.collection('users').doc(userId).get();
      const fcmToken = userDoc.data()?.fcmToken;

      if (!fcmToken) {
        res.status(404).send('FCM 토큰을 찾을 수 없습니다.');
        return;
      }

      const message = {
        token: fcmToken,
        notification: {
          title: title || '테스트 알림',
          body: body || '이것은 테스트 알림입니다.'
        }
      };

      const response = await admin.messaging().send(message);
      res.json({ success: true, messageId: response });

    } catch (error) {
      console.error('테스트 알림 오류:', error);
      res.status(500).json({ error: error.message });
    }
  });


// const studyRoomApi = require('./studyRoomApi');
// const studyRoomTriggers = require('./studyRoomTriggers');




// 스터디룸 API export
exports.studyRoomApi = studyRoomApi.api;

// 스터디룸 트리거 함수들 export
exports.updateStudyRoomProgressOnStudy = studyRoomTriggers.updateStudyRoomProgressOnStudy;
exports.updateWrongCountOnStateChange = studyRoomTriggers.updateWrongCountOnStateChange;
// exports.resetDailyStatus = studyRoomTriggers.resetDailyStatus;
exports.sendDailyReminder = studyRoomTriggers.sendDailyReminder;

// 스터디룸 - 프로필 변경 관련
exports.onUserUpdate = studyRoomTriggers.onUserUpdate;

// 스터디룸 진도율 관련 -> 계산( 복습용 단어 / 전체 단어 )
exports.updateProgressOnReviewWordChange =
  studyRoomTriggers.updateProgressOnReviewWordChange;

// 스터디룸 - 파이어베이스에 동기화
exports.syncProgressOnWrongWrite  = studyRoomTriggers.syncProgressOnWrongWrite;
exports.syncProgressOnReviewWrite = studyRoomTriggers.syncProgressOnReviewWrite;

