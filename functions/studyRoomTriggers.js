// studyRoomTriggers.js - 스터디룸 자동 동기화 트리거

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { calculateUserProgress } = require('./utils/progress');

// Firestore 참조
const db = admin.firestore();

// ========== 헬퍼 함수들 ==========


const convertUidToNumericId = (uid) => {
  const hash = uid.substring(0, 8).split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
  return hash % 100000;
};


// 사용자의 학습 진도 계산 (studyRoomApi.js와 동일)
// 이 함수로 교체해주세요.

// 따로 util에서 받아옴

// const calculateUserProgress = async (userId) => {
//   try {
//     // 1. 전체 단어 수 (변경 없음)
//     const wordsSnapshot = await db.collection('users').doc(userId).collection('words').get();
//     const totalWords = wordsSnapshot.size;

//     if (totalWords === 0) {
//       return {
//         progressRate: 0,
//         todayWrongCount: 0,
//         stageDistribution: { stage0: 0, stage1: 0, stage2: 0, stage3: 0, stage4: 0, stage5: 0 }
//       };
//     }

//     // 2. 학습한 단어 정보 가져오기 (🔥 수정: individual_states -> review_words)
//     // 복습용 단어에서 가져오기
//     const reviewWordsSnapshot = await db.collection('users').doc(userId).collection('review_words').get();
//     const studiedWords = reviewWordsSnapshot.size; // '학습한 단어 수'는 review_words의 총 개수

//     // 3. 오늘 틀린 단어 수 가져오기 (변경 없음, individual_states 사용)
//     // 그 날 틀린 단어는 개인용 단어장을 넘어가기 때문
//     const wrongWordsSnapshot = await db.collection('users').doc(userId).collection('individual_states').get();
//     const todayWrongCount = wrongWordsSnapshot.size;

//     // 4. 단계별 분포 계산 (🔥 수정: individual_states -> review_words)
//     const stageDistribution = { stage0: 0, stage1: 0, stage2: 0, stage3: 0, stage4: 0, stage5: 0 };
//     reviewWordsSnapshot.forEach(doc => {
//       const stage = doc.data().stage || 0;
//       if (stage >= 1 && stage <= 5) {
//         stageDistribution[`stage${stage}`]++;
//       }
//     });
//     // 미학습 단어 수는 '전체 단어 - 학습한 단어'
//     stageDistribution.stage0 = totalWords - studiedWords;

//     // 5. 진도율 계산 (🔥 수정: 새로운 studiedWords 값 사용)
//     const progressRate = totalWords > 0 ? Math.round((studiedWords / totalWords) * 100) : 0;

//     return {
//       progressRate,
//       todayWrongCount,
//       stageDistribution
//     };

//   } catch (error) {
//     console.error('진도 계산 오류:', error);
//     return {
//       progressRate: 0,
//       todayWrongCount: 0,
//       stageDistribution: { stage0: 0, stage1: 0, stage2: 0, stage3: 0, stage4: 0, stage5: 0 }
//     };
//   }
// };

// ========== Firestore 트리거 함수들 ==========

// 1. 학습 이력 추가 시 스터디룸 진도 업데이트
exports.updateStudyRoomProgressOnStudy = functions
  .region('asia-northeast3')
  .firestore
  .document('users/{userId}/study_history/{historyId}')
  .onCreate(async (snap, context) => {
    const userId = context.params.userId;
    console.log(`[최적화] 학습 이력 추가 감지: userId=${userId}`);
    
    try {
      const userRoomsSnapshot = await db.collection('userStudyRooms').doc(userId).collection('rooms').get();
      if (userRoomsSnapshot.empty) return;

      const batch = db.batch();
      
      // 각 스터디룸의 멤버 정보 업데이트
      userRoomsSnapshot.docs.forEach(roomDoc => {
        const roomId = roomDoc.id;
        const memberRef = db.collection('studyRoomMembers').doc(roomId).collection('members').doc(userId);
        batch.update(memberRef, {
          'dailyProgress.hasStudiedToday': true,
          'dailyProgress.lastUpdated': admin.firestore.FieldValue.serverTimestamp()
        });
      });
      
      // users 컬렉션의 상태도 업데이트
      batch.update(db.collection('users').doc(userId), {
        hasStudiedToday: true,
        lastStudiedAt: admin.firestore.FieldValue.serverTimestamp() // [중요] 타임스탬프 기록
      });
      
      await batch.commit();
      console.log(`[최적화] ${userRoomsSnapshot.size}개 스터디룸의 '학습 여부' 업데이트 완료`);

    } catch (error) {
      console.error('스터디룸 진도 업데이트(최적화) 오류:', error);
    }
  });

// 2. individual_states 변경 시 틀린 개수 업데이트
exports.updateWrongCountOnStateChange = functions
  .region('asia-northeast3')
  .firestore
  .document('users/{userId}/individual_states/{wordId}')
  .onWrite(async (change, context) => {
    const userId = context.params.userId;
    console.log(`[최적화] individual_states 변경 감지: userId=${userId}`);
    
    try {
      // 1. 여기서 전체 학습 현황을 다시 계산합니다.
      const progress = await calculateUserProgress(userId);

      // 2. 사용자가 속한 모든 스터디룸을 찾습니다.
      const userRoomsSnapshot = await db.collection('userStudyRooms').doc(userId).collection('rooms').get();
      if (userRoomsSnapshot.empty) {
        console.log('사용자가 속한 스터디룸이 없어 업데이트를 종료합니다.');
        return;
      }

      const batch = db.batch();
      
      // 3. 각 스터디룸의 멤버 정보에 새로운 학습 현황 전체를 업데이트합니다.
      userRoomsSnapshot.docs.forEach(roomDoc => {
        const roomId = roomDoc.id;
        const memberRef = db.collection('studyRoomMembers').doc(roomId).collection('members').doc(userId);
        
        // dailyProgress 객체 전체를 새로운 현황으로 업데이트
        batch.update(memberRef, {
          'dailyProgress.progressRate': progress.progressRate,
          'dailyProgress.todayWrongCount': progress.todayWrongCount,
          'dailyProgress.stageDistribution': progress.stageDistribution, // 단계별 분포도 함께 업데이트
          'dailyProgress.lastUpdated': admin.firestore.FieldValue.serverTimestamp()
        });
      });
      
      await batch.commit();
      console.log(`[확장] ${userRoomsSnapshot.size}개 스터디룸의 '전체 진도' 업데이트 완료`);
      
    } catch (error) {
      console.error('전체 진도 업데이트(확장) 오류:', error);
    }
  });


  // 2.1  review_words onWrite → 진도율 동기화
exports.updateProgressOnReviewWordChange = functions
  .region('asia-northeast3')
  .firestore
  .document('users/{userId}/review_words/{wordId}')
  .onWrite(async (change, context) => {
    const userId = context.params.userId;
    console.log(`[확장] review_words 변경 감지: userId=${userId}`);

    // ① 새 통계 계산
    const progress = await calculateUserProgress(userId);

    // ② 사용자가 속한 스터디룸 찾기
    const userRoomsSnapshot = await db
      .collection('userStudyRooms').doc(userId).collection('rooms').get();
    if (userRoomsSnapshot.empty) return;

    // ③ 각 멤버 문서 업데이트
    const batch = db.batch();
    userRoomsSnapshot.docs.forEach(roomDoc => {
      const memberRef = db.collection('studyRoomMembers')
        .doc(roomDoc.id).collection('members').doc(userId);
      batch.update(memberRef, {
        'dailyProgress.progressRate': progress.progressRate,
        'dailyProgress.stageDistribution': progress.stageDistribution,
        'dailyProgress.todayWrongCount'   : progress.todayWrongCount,
        'dailyProgress.lastUpdated': admin.firestore.FieldValue.serverTimestamp()
      });
    });
    await batch.commit();
    console.log(`[확장] ${userRoomsSnapshot.size}개 방 진도율 업데이트 완료`);
  });
  
  // 틀린 갯수 문서(파이어베이스) 동기화
  exports.syncProgressOnWrongWrite = functions
  .region('asia-northeast3')
  .firestore.document('users/{uid}/individual_states/{wordId}')
  .onWrite(async (_, ctx) => {
    const uid = ctx.params.uid;
    const progress = await calculateUserProgress(uid);      // progressRate + todayWrongCount + stageDist

    const rooms = await db.collectionGroup('members')
                   .where('uid', '==', uid).get();

    const batch = db.batch();
    rooms.forEach(doc => batch.update(doc.ref, { dailyProgress: progress })); // ←★ 이 한 줄!
    return batch.commit();
});

  // 진행률 문서(파이어베이스) 동기화
  exports.syncProgressOnReviewWrite = functions
  .region('asia-northeast3')
  .firestore.document('users/{uid}/review_words/{wordId}')
  .onWrite(async (_, ctx) => {
    const uid = ctx.params.uid;
    const progress = await calculateUserProgress(uid);

    const rooms = await db.collectionGroup('members')
                   .where('uid', '==', uid).get();

    const batch = db.batch();
    rooms.forEach(doc => batch.update(doc.ref, { dailyProgress: progress })); 
    return batch.commit();
});


// 메인 화면에서 할듯
// 3. 매일 자정 일일 상태 초기화
// exports.resetDailyStatus = functions
//   .region('asia-northeast3')
//   .pubsub
//   .schedule('0 0 * * *')
//   .timeZone('Asia/Seoul')
//   .onRun(async (context) => {
//     console.log('일일 상태 초기화 시작');
    
//     try {
//       // 1) 모든 사용자의 hasStudiedToday 초기화
//       const usersSnapshot = await db.collection('users').get();
      
//       if (!usersSnapshot.empty) {
//         // 사용자가 많을 경우 배치로 나누어 처리
//         const userBatches = [];
//         let currentBatch = db.batch();
//         let batchCount = 0;
        
//         usersSnapshot.docs.forEach(doc => {
//           currentBatch.update(doc.ref, {
//             hasStudiedToday: false
//           });
//           batchCount++;
          
//           // 배치당 최대 500개 작업
//           if (batchCount === 500) {
//             userBatches.push(currentBatch);
//             currentBatch = db.batch();
//             batchCount = 0;
//           }
//         });
        
//         if (batchCount > 0) {
//           userBatches.push(currentBatch);
//         }
        
//         // 모든 배치 실행
//         for (const batch of userBatches) {
//           await batch.commit();
//         }
        
//         console.log(`${usersSnapshot.size}명의 사용자 일일 상태 초기화 완료`);
//       }
      
//       // 2) 모든 스터디룸 멤버의 일일 상태 초기화
//       const roomsSnapshot = await db.collection('studyRooms').get();
      
//       for (const roomDoc of roomsSnapshot.docs) {
//         const roomId = roomDoc.id;
//         const membersSnapshot = await db.collection('studyRoomMembers')
//           .doc(roomId)
//           .collection('members')
//           .get();
        
//         if (!membersSnapshot.empty) {
//           const membersBatch = db.batch();
          
//           membersSnapshot.docs.forEach(memberDoc => {
//             membersBatch.update(memberDoc.ref, {
//               'dailyProgress.hasStudiedToday': false,
//               'dailyProgress.todayWrongCount': 0,
//               'dailyProgress.lastUpdated': admin.firestore.FieldValue.serverTimestamp()
//             });
//           });
          
//           await membersBatch.commit();
//           console.log(`방 ${roomId}: ${membersSnapshot.size}명 초기화 완료`);
//         }
//       }
      
//       // 3) individual_states 초기화 (선택사항)
//       // 필요한 경우 여기에서 individual_states 컬렉션 정리
      
//       console.log('일일 상태 초기화 완료');
      
//     } catch (error) {
//       console.error('일일 상태 초기화 오류:', error);
//     }
//   });

// 4. 매일 오후 9시 학습 미완료자 알림
exports.sendDailyReminder = functions
  .region('asia-northeast3')
  .pubsub
  .schedule('0 21 * * *')
  .timeZone('Asia/Seoul')
  .onRun(async (context) => {
    console.log('[최적화] 일일 학습 리마인더 시작');
    
    try {
      // 한국 시간 기준 오늘 자정 시간 계산
      const now = new Date();
      const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);

      // 오늘 아직 학습하지 않은 사용자들을 쿼리
      const usersToRemindSnapshot = await db.collection('users')
        .where('lastStudiedAt', '<', todayStart)
        .get();

      if (usersToRemindSnapshot.empty) {
        console.log('알림을 보낼 사용자가 없습니다.');
        return;
      }

      const messages = [];
      
      // 각 사용자에 대해 알림 메시지 생성
      for (const userDoc of usersToRemindSnapshot.docs) {
        const userData = userDoc.data();
        if (userData.fcmToken) {
           messages.push({
              token: userData.fcmToken,
              notification: {
                title: '🌙 오늘의 학습을 완료하세요!',
                body: `목표 달성까지 조금만 더! 오늘의 학습을 마무리하고 성취감을 느껴보세요.`
              },
              data: { type: 'DAILY_REMINDER' },
              android: { /* ... */ }
            });
        }
      }

      if (messages.length > 0) {
        await admin.messaging().sendAll(messages);
        console.log(`[최적화] 총 ${messages.length}명에게 일일 리마인더 전송 완료`);
      }

    } catch (error) {
      console.error('일일 리마인더 전송(최적화) 오류:', error);
    }
  });

// 5. 새 멤버 추가 시 초기 데이터 동기화 
exports.syncNewMemberData = functions
  .region('asia-northeast3')
  .firestore
  .document('studyRoomMembers/{roomId}/members/{userId}')
  .onCreate(async (snap, context) => {
    const userId = context.params.userId;
    const roomId = context.params.roomId;
    const memberData = snap.data();
    
    console.log(`새 멤버 추가: roomId=${roomId}, userId=${userId}`);
    
    // OWNER나 MEMBER 역할인 경우만 처리
    if (memberData.role === 'OWNER' || memberData.role === 'MEMBER') {
      try {
        // 이미 dailyProgress가 설정되어 있으면 스킵
        if (memberData.dailyProgress && memberData.dailyProgress.progressRate !== undefined) {
          console.log('이미 초기 데이터가 설정되어 있습니다.');
          return;
        }
        
        // 사용자의 현재 학습 통계 가져오기
        const progress = await calculateUserProgress(userId);
        
        // 사용자의 오늘 학습 여부 확인
        const userDoc = await db.collection('users').doc(userId).get();
        const hasStudiedToday = userDoc.data()?.hasStudiedToday || false;
        
        // 멤버 정보 업데이트
        await snap.ref.update({
          'dailyProgress.hasStudiedToday': hasStudiedToday,
          'dailyProgress.progressRate': progress.progressRate,
          'dailyProgress.todayWrongCount': progress.todayWrongCount,
          'dailyProgress.stageDistribution': progress.stageDistribution,
          'dailyProgress.lastUpdated': admin.firestore.FieldValue.serverTimestamp()
        });
        
        console.log(`멤버 초기 데이터 동기화 완료: progressRate=${progress.progressRate}%`);
        
      } catch (error) {
        console.error('새 멤버 데이터 동기화 오류:', error);
      }
    }
  })



// 사용자의 상태 변경에 따른 처리
exports.onUserUpdate = functions
  .region('asia-northeast3')
  .firestore
  .document('users/{userId}')
  .onUpdate(async (change, context) => {
    // 변경 전후 데이터와 사용자 ID를 가져옵니다.
    const userId = context.params.userId;
    const beforeData = change.before.data();
    const afterData = change.after.data();

    // 여러 DB 업데이트를 하나로 묶기 위한 batch 생성
    const batch = db.batch();
    // 실제로 변경할 내용이 있을 때만 batch를 실행하기 위한 플래그
    let needsCommit = false;

    // --- 1. 닉네임 변경 감지 및 동기화 ---
    if (beforeData.nickname !== afterData.nickname) {
      const newNickname = afterData.nickname;
      console.log(`닉네임 변경 감지: ${userId}, ${beforeData.nickname} -> ${newNickname}`);
      
      // 사용자가 참여한 모든 스터디룸을 찾습니다.
      const userRoomsSnapshot = await db.collection('userStudyRooms').doc(userId).collection('rooms').get();
      // 각 스터디룸의 멤버 정보 문서에 새 닉네임을 업데이트합니다.
      userRoomsSnapshot.docs.forEach(roomDoc => {
        const memberRef = db.collection('studyRoomMembers').doc(roomDoc.id).collection('members').doc(userId);
        batch.update(memberRef, { nickname: newNickname });
      });

      // 사용자가 방장인 모든 스터디룸을 찾아서 방장 닉네임을 업데이트합니다.
      const ownedRoomsSnapshot = await db.collection('studyRooms').where('ownerId', '==', userId).get();
      ownedRoomsSnapshot.forEach(roomDoc => {
        batch.update(roomDoc.ref, { ownerNickname: newNickname });
      });

      // numericIdMap에서도 닉네임을 업데이트합니다.
      const numericId = convertUidToNumericId(userId);
      const numericIdMapRef = db.collection('numericIdMap').doc(String(numericId));
      batch.update(numericIdMapRef, { nickname: newNickname });
      
      needsCommit = true; // 업데이트할 내용이 있음을 표시
    }

    // --- 2. hasStudiedToday 상태 변경 감지 및 동기화 (핵심 로직) ---
    if (beforeData.hasStudiedToday !== afterData.hasStudiedToday) {
      console.log(`hasStudiedToday 변경 감지: ${userId} -> ${afterData.hasStudiedToday}`);
      
      // 사용자가 참여한 모든 스터디룸을 찾습니다.
      const userRoomsSnapshot = await db.collection('userStudyRooms').doc(userId).collection('rooms').get();
      
      // 각 스터디룸의 멤버 정보 문서에 새 학습 상태를 업데이트합니다.
      userRoomsSnapshot.docs.forEach(roomDoc => {
        const memberRef = db.collection('studyRoomMembers').doc(roomDoc.id).collection('members').doc(userId);
        // 'dailyProgress' 객체 안의 'hasStudiedToday' 필드를 업데이트
        batch.update(memberRef, { 'dailyProgress.hasStudiedToday': afterData.hasStudiedToday });
      });

      needsCommit = true; // 업데이트할 내용이 있음을 표시
    }

    // 변경 사항이 하나라도 있었을 경우에만 최종적으로 DB에 반영합니다.
    if (needsCommit) {
      await batch.commit();
      console.log(`사용자 ${userId} 정보 동기화 완료.`);
    }

    return null;
  });
