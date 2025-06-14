// studyRoomApi.js - 스터디룸 REST API

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const express = require('express');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const { calculateUserProgress } = require('./utils/progress');

// Firestore 참조
const db = admin.firestore();

// Express 앱 생성
const app = express();
app.use(cors({ origin: true }));
app.use(express.json());

// ========== 인증 미들웨어 ==========
const authenticateUser = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ success: false, message: '인증 토큰이 없습니다.' });
    }
    
    const token = authHeader.split('Bearer ')[1];
    const decodedToken = await admin.auth().verifyIdToken(token);
    req.user = decodedToken;
    next();
  } catch (error) {
    console.error('인증 오류:', error);
    return res.status(401).json({ success: false, message: '인증 실패' });
  }
};

// ========== 헬퍼 함수들 ==========

// 사용자 정보 가져오기
const getUserInfo = async (userId) => {
  const userDoc = await db.collection('users').doc(userId).get();
  if (!userDoc.exists) {
    throw new Error('사용자 정보를 찾을 수 없습니다.');
  }
  const userData = userDoc.data();
  
  //  numericIdMap 자동 생성/업데이트
  const numericId = convertUidToNumericId(userId);
  await db.collection('numericIdMap').doc(String(numericId)).set({
    userId: userId,
    nickname: userData.nickname || 'Unknown',
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  
  return {
    nickname: userData.nickname || 'Unknown',
    email: userData.email,
    profileImage: userData.profileImage || null,
    fcmToken: userData.fcmToken,
    hasStudiedToday: userData.hasStudiedToday || false
  };
};


// 따로 util에서 받아옴
// const stageDistribution = {
//   stage0: 0, stage1: 0, stage2: 0,
//   stage3: 0, stage4: 0, stage5: 0, stage6: 0 //  stage6 추가
// };

// 단계 카운트 조건 변경
// if (stage >= 1 && stage <= 6) {
//   stageDistribution[`stage${stage}`]++;
// }

// stage0 계산 시 음수 방지
// stageDistribution.stage0 = Math.max(0, 200 - studiedWords);

// Firebase UID를 숫자 ID로 변환
const convertUidToNumericId = (uid) => {
  const hash = uid.substring(0, 8).split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
  return hash % 100000;
};

// 숫자 ID로 Firebase UID 찾기
const findUserIdByNumericId = async (numericId) => {
  try {
    const numericIdStr = String(numericId);
    const mapDoc = await db.collection('numericIdMap').doc(numericIdStr).get();

    if (mapDoc.exists) {
      return mapDoc.data().userId;
    }
    return null;
  } catch (error) {
    console.error(`[성능 개선] Numeric ID(${numericId})로 UID 찾기 오류:`, error);
    return null;
  }
};
// ========== API 엔드포인트들 ==========

// 1. 스터디룸 생성
app.post('/api/studyrooms', authenticateUser, async (req, res) => {
  try {
    const { title, password } = req.body;
    const userId = req.user.uid;
    
    console.log(`방 생성 요청: title=${title}, userId=${userId}`);
    
    // 입력 검증
    if (!title || !password) {
      return res.status(400).json({ 
        success: false, 
        message: '제목과 비밀번호를 입력해주세요.' 
      });
    }
    
    // 중복 제목 체크
    const existingRoom = await db.collection('studyRooms')
      .where('title', '==', title)
      .get();
    
    if (!existingRoom.empty) {
      return res.status(400).json({ 
        success: false, 
        message: '이미 존재하는 방 제목입니다.' 
      });
    }
    
    // 사용자 정보와 진도 가져오기 이때 numericIdMap 자동 생성
    const userInfo = await getUserInfo(userId);
    const progress = await calculateUserProgress(userId);
    
    // 비밀번호 해싱
    const hashedPassword = await bcrypt.hash(password, 10);
    
    // 새 방 문서 생성
    const roomRef = db.collection('studyRooms').doc();
    const roomData = {
      title,
      password: hashedPassword,
      ownerId: userId,
      ownerNickname: userInfo.nickname,
      memberCount: 1,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      settings: { maxMembers: 30 }
    };
    
    // 배치 작업 시작
    const batch = db.batch();
    
    // 1) 방 생성
    batch.set(roomRef, roomData);
    
    // 2) 방장을 멤버로 추가
    const memberRef = db.collection('studyRoomMembers')
      .doc(roomRef.id)
      .collection('members')
      .doc(userId);
    
    batch.set(memberRef, {
      nickname: userInfo.nickname,
      profileImage: userInfo.profileImage,
      role: 'OWNER',
      joinedAt: admin.firestore.FieldValue.serverTimestamp(),
      dailyProgress: {
        hasStudiedToday: userInfo.hasStudiedToday,
        progressRate: progress.progressRate,
        todayWrongCount: progress.todayWrongCount,
        lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
        stageDistribution: progress.stageDistribution
      }
    });
    
    // 3) 사용자의 방 목록에 추가
    const userRoomRef = db.collection('userStudyRooms')
      .doc(userId)
      .collection('rooms')
      .doc(roomRef.id);
    
    batch.set(userRoomRef, {
      title,
      isOwner: true,
      joinedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    // 배치 실행
    await batch.commit();
    
    console.log(`방 생성 성공: roomId=${roomRef.id}`);
    
    return res.status(201).json({
      success: true,
      title,
      ownerNickname: userInfo.nickname,
      ownerId: convertUidToNumericId(userId),
      message: '스터디룸이 생성되었습니다.'
    });
    
  } catch (error) {
    console.error('방 생성 오류:', error);
    return res.status(500).json({ 
      success: false, 
      message: '스터디룸 생성 중 오류가 발생했습니다.' 
    });
  }
});

// 2. 스터디룸 참여
app.post('/api/studyrooms/join', authenticateUser, async (req, res) => {
  try {
    const { title, password } = req.body;
    const userId = req.user.uid;
    console.log(`방 참여 요청: title=${title}, userId=${userId}`);
    const roomQuery = await db.collection('studyRooms').where('title', '==', title).limit(1).get();
    if (roomQuery.empty) {
      return res.status(404).json({ success: false, message: '존재하지 않는 방입니다.' });
    }
    const roomDoc = roomQuery.docs[0];
    const roomData = roomDoc.data();
    const roomId = roomDoc.id;

    // [추가된 로직] 방 인원 제한 확인
    if (roomData.memberCount >= roomData.settings.maxMembers) {
      return res.status(400).json({
        success: false,
        message: '방 인원이 가득 찼습니다.'
      });
    }

    const isPasswordValid = await bcrypt.compare(password, roomData.password);
    if (!isPasswordValid) {
      return res.status(400).json({ success: false, message: '비밀번호가 일치하지 않습니다.' });
    }
    const memberDoc = await db.collection('studyRoomMembers').doc(roomId).collection('members').doc(userId).get();
    if (memberDoc.exists) {
      return res.status(400).json({ success: false, message: '이미 참여중인 방입니다.' });
    }

    // getUserInfo 호출 시 자동으로 numericIdMap 생성
    const userInfo = await getUserInfo(userId);
    const progress = await calculateUserProgress(userId);
    const batch = db.batch();
    const memberRef = db.collection('studyRoomMembers').doc(roomId).collection('members').doc(userId);
    batch.set(memberRef, {
      nickname: userInfo.nickname,
      profileImage: userInfo.profileImage,
      role: 'MEMBER',
      joinedAt: admin.firestore.FieldValue.serverTimestamp(),
      dailyProgress: {
        hasStudiedToday: userInfo.hasStudiedToday,
        progressRate: progress.progressRate,
        todayWrongCount: progress.todayWrongCount,
        lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
        stageDistribution: progress.stageDistribution
      }
    });
    batch.update(roomDoc.ref, { memberCount: admin.firestore.FieldValue.increment(1) });
    const userRoomRef = db.collection('userStudyRooms').doc(userId).collection('rooms').doc(roomId);
    batch.set(userRoomRef, {
      title: roomData.title,
      isOwner: false,
      joinedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    await batch.commit();

    console.log(`방 참여 성공: roomId=${roomId}`);
    const roomDetails = await getRoomDetailsInternal(roomId, userId);
    return res.json({ success: true, message: '스터디룸에 참여했습니다.', roomDetails });
  } catch (error) {
    console.error('방 참여 오류:', error);
    return res.status(500).json({ success: false, message: '스터디룸 참여 중 오류가 발생했습니다.' });
  }
});
// 3. 내 스터디룸 목록
app.get('/api/studyrooms/my', authenticateUser, async (req, res) => {
  try {
    const userId = req.user.uid;
    console.log(`내 방 목록 조회: userId=${userId}`);
    const userRoomsSnapshot = await db.collection('userStudyRooms').doc(userId).collection('rooms').orderBy('joinedAt', 'desc').get();
    
    const roomIds = userRoomsSnapshot.docs.map(doc => doc.id);

    if (roomIds.length === 0) {
      return res.json([]);
    }

    // `where in` 쿼리로 모든 방 정보를 한 번에 가져오기
    const roomsSnapshot = await db.collection('studyRooms').where(admin.firestore.FieldPath.documentId(), 'in', roomIds).get();
    
    const roomsData = {};
    roomsSnapshot.forEach(doc => {
      roomsData[doc.id] = doc.data();
    });

    const myRooms = roomIds.map(roomId => {
      const roomData = roomsData[roomId];
      return roomData ? {
        title: roomData.title,
        ownerNickname: roomData.ownerNickname,
        ownerId: convertUidToNumericId(roomData.ownerId),
        memberCount: roomData.memberCount
      } : null;
    }).filter(Boolean); // 혹시 모를 null 값 제거
    
    console.log(`내 방 목록 조회 완료: ${myRooms.length}개`);
    return res.json(myRooms);
  } catch (error) {
    console.error('내 방 목록 조회 오류:', error);
    return res.status(500).json([]);
  }
});

// 4. 스터디룸 상세 정보
app.get('/api/studyrooms/details', authenticateUser, async (req, res) => {
  try {
    const { title } = req.query;
    const userId = req.user.uid;
    
    console.log(`방 상세 조회: title=${title}, userId=${userId}`);
    
    if (!title) {
      return res.status(400).json({ 
        success: false, 
        message: '방 제목을 입력해주세요.' 
      });
    }
    
    // 방 찾기
    const roomQuery = await db.collection('studyRooms')
      .where('title', '==', title)
      .limit(1)
      .get();
    
    if (roomQuery.empty) {
      return res.status(404).json({ 
        success: false, 
        message: '존재하지 않는 방입니다.' 
      });
    }
    
    const roomId = roomQuery.docs[0].id;
    const roomDetails = await getRoomDetailsInternal(roomId, userId);
    
    return res.json(roomDetails);
    
  } catch (error) {
    console.error('방 상세 조회 오류:', error);
    return res.status(500).json({ 
      success: false, 
      message: '방 정보 조회 중 오류가 발생했습니다.' 
    });
  }
});

// 방 상세 정보 가져오기 (내부 함수)
async function getRoomDetailsInternal(roomId, userId) {
  const roomDoc = await db.collection('studyRooms').doc(roomId).get();
  const roomData = roomDoc.data();
  const membersSnapshot = await db.collection('studyRoomMembers').doc(roomId).collection('members').get();

  // 1. 모든 멤버의 단어 수 조회 요청을 미리 배열에 담음
  const promises = membersSnapshot.docs.map(doc =>
    db.collection('users').doc(doc.id).collection('words').count().get()
  );

  // 2. Promise.all로 모든 요청을 병렬 처리
  const wordsCountSnapshots = await Promise.all(promises);

  // 3. 이제 모든 데이터를 조합한다.
  const members = membersSnapshot.docs.map((doc, index) => {
    const memberData = doc.data();
    const dailyProgress = memberData.dailyProgress || {};
    
    // Firestore v2 SDK의 .count()를 사용
    const totalWordCount = wordsCountSnapshots[index].data().count || 100;

    return {
      userId: convertUidToNumericId(doc.id),
      nickname: memberData.nickname,
      role: memberData.role,
      profileImage: memberData.profileImage,
      progress: {
        totalWordCount: totalWordCount,
        studiedWordCount: Math.round((dailyProgress.progressRate || 0) * totalWordCount / 100),
        redStageWordCount: dailyProgress.todayWrongCount || 0
      },
      dailyStatus: {
        isStudiedToday: dailyProgress.hasStudiedToday || false
      }
    };
  });

  return {
    title: roomData.title,
    ownerNickname: roomData.ownerNickname,
    ownerId: convertUidToNumericId(roomData.ownerId),
    isAdmin: roomData.ownerId === userId,
    members
  };
}

// 5. 멤버 강퇴
app.delete('/api/studyrooms/members', authenticateUser, async (req, res) => {
  try {
    const { roomTitle, memberUserId } = req.query;
    const ownerId = req.user.uid;
    
    console.log(`멤버 강퇴 요청: roomTitle=${roomTitle}, memberUserId=${memberUserId}`);
    
    // 방장 권한 확인
    const roomQuery = await db.collection('studyRooms')
      .where('title', '==', roomTitle)
      .where('ownerId', '==', ownerId)
      .limit(1)
      .get();
    
    if (roomQuery.empty) {
      return res.status(403).json({ 
        success: false, 
        message: '권한이 없거나 존재하지 않는 방입니다.' 
      });
    }
    
    const roomDoc = roomQuery.docs[0];
    const roomId = roomDoc.id;
    
    // 대상 사용자 찾기
    const targetUserId = await findUserIdByNumericId(memberUserId);
    if (!targetUserId) {
      return res.status(404).json({ 
        success: false, 
        message: '멤버를 찾을 수 없습니다.' 
      });
    }
    
    // 배치 작업
    const batch = db.batch();
    
    // 1) 멤버 삭제
    batch.delete(
      db.collection('studyRoomMembers')
        .doc(roomId)
        .collection('members')
        .doc(targetUserId)
    );
    
    // 2) 멤버 수 감소
    batch.update(roomDoc.ref, {
      memberCount: admin.firestore.FieldValue.increment(-1)
    });
    
    // 3) 사용자 방 목록에서 삭제
    batch.delete(
      db.collection('userStudyRooms')
        .doc(targetUserId)
        .collection('rooms')
        .doc(roomId)
    );
    
    await batch.commit();
    
    console.log(`멤버 강퇴 성공: targetUserId=${targetUserId}`);
    
    return res.json({ 
      success: true, 
      message: '멤버를 강퇴했습니다.' 
    });
    
  } catch (error) {
    console.error('멤버 강퇴 오류:', error);
    return res.status(500).json({ 
      success: false, 
      message: '멤버 강퇴 중 오류가 발생했습니다.' 
    });
  }
});

// 6. 스터디룸 나가기
app.post('/api/studyrooms/leave', authenticateUser, async (req, res) => {
  try {
    const { title } = req.body;
    const userId = req.user.uid;
    
    console.log(`방 나가기 요청: title=${title}, userId=${userId}`);
    
    const roomQuery = await db.collection('studyRooms')
      .where('title', '==', title)
      .limit(1)
      .get();
    
    if (roomQuery.empty) {
      return res.status(404).json({ 
        success: false, 
        message: '존재하지 않는 방입니다.' 
      });
    }
    
    const roomDoc = roomQuery.docs[0];
    const roomId = roomDoc.id;
    const roomData = roomDoc.data();
    
    // 방장인 경우 특별 처리
    if (roomData.ownerId === userId) {
      const membersSnapshot = await db.collection('studyRoomMembers')
        .doc(roomId)
        .collection('members')
        .get();
      
      if (membersSnapshot.size === 1) {
        // 방장 혼자만 있는 경우 - 방 완전 삭제
        const batch = db.batch();
        
        // 1. 방 문서 삭제
        batch.delete(roomDoc.ref);
        
        // 2. 모든 멤버 정보 삭제
        membersSnapshot.docs.forEach(doc => {
          batch.delete(doc.ref);
        });
        
        // 3. 방장의 userStudyRooms에서 삭제
        batch.delete(
          db.collection('userStudyRooms')
            .doc(userId)
            .collection('rooms')
            .doc(roomId)
        );
        
        await batch.commit();
        
        console.log(`방 완전 삭제: roomId=${roomId}`);
        
        return res.json({ 
          success: true, 
          message: '방이 삭제되었습니다.' 
        });
        
      } else {
        // 다른 멤버가 있는 경우 - 방장 권한 이전
        const nextOwner = membersSnapshot.docs.find(doc => 
          doc.id !== userId && doc.data().role === 'MEMBER'
        );
        
        if (nextOwner) {
          const batch = db.batch();
          const newOwnerData = nextOwner.data();
          
          // 새 방장 지정
          batch.update(nextOwner.ref, { role: 'OWNER' });
          
          // 방 정보 업데이트
          batch.update(roomDoc.ref, {
            ownerId: nextOwner.id,
            ownerNickname: newOwnerData.nickname
          });

          // 새 방장의 userStudyRooms 업데이트
          batch.update(
            db.collection('userStudyRooms')
              .doc(nextOwner.id)
              .collection('rooms')
              .doc(roomId),
            { isOwner: true }
          );
          
          // 기존 방장 제거
          batch.delete(
            db.collection('studyRoomMembers')
              .doc(roomId)
              .collection('members')
              .doc(userId)
          );
          
          batch.delete(
            db.collection('userStudyRooms')
              .doc(userId)
              .collection('rooms')
              .doc(roomId)
          );
          
          batch.update(roomDoc.ref, {
            memberCount: admin.firestore.FieldValue.increment(-1)
          });
          
          await batch.commit();
          
          console.log(`방장 권한 이전: roomId=${roomId}, 새 방장=${nextOwner.id}`);
          
          return res.json({ 
            success: true, 
            message: '방장 권한이 다음 멤버에게 이전되었습니다.' 
          });
        }
      }
    } else {
      // 일반 멤버가 나가는 경우 (else 추가!)
      const batch = db.batch();
      
      // 1) 멤버 삭제
      batch.delete(
        db.collection('studyRoomMembers')
          .doc(roomId)
          .collection('members')
          .doc(userId)
      );
      
      // 2) 멤버 수 감소
      batch.update(roomDoc.ref, {
        memberCount: admin.firestore.FieldValue.increment(-1)
      });
      
      // 3) 사용자 방 목록에서 삭제
      batch.delete(
        db.collection('userStudyRooms')
          .doc(userId)
          .collection('rooms')
          .doc(roomId)
      );
      
      await batch.commit();
      
      console.log(`일반 멤버 나가기 성공: roomId=${roomId}`);
      
      return res.json({ 
        success: true, 
        message: '스터디룸에서 나갔습니다.' 
      });
    }
    
  } catch (error) {
    console.error('방 나가기 오류:', error);
    return res.status(500).json({ 
      success: false, 
      message: '방 나가기 중 오류가 발생했습니다.' 
    });
  }
});

// 7. 스터디룸 검색
app.get('/api/studyrooms/search', authenticateUser, async (req, res) => {
  try {
    const { query } = req.query;
    const userId = req.user.uid;
    
    console.log(`방 검색: query=${query}`);
    
    if (!query || query.trim().length === 0) {
      return res.json([]);
    }
    
    // 사용자가 참여한 방 목록
    const userRoomsSnapshot = await db.collection('userStudyRooms')
      .doc(userId)
      .collection('rooms')
      .get();
    
    const userRoomIds = new Set(userRoomsSnapshot.docs.map(doc => doc.id));
    
    // 모든 방 가져오기
    const roomsSnapshot = await db.collection('studyRooms')
      .orderBy('title')
      .get();
    
    const searchResults = [];
    
    roomsSnapshot.docs.forEach(doc => {
      const roomData = doc.data();
      const roomId = doc.id;
      
      // 제목에 검색어 포함 & 참여하지 않은 방
      if (roomData.title.toLowerCase().includes(query.toLowerCase()) && 
          !userRoomIds.has(roomId)) {
        searchResults.push({
          title: roomData.title,
          ownerNickname: roomData.ownerNickname,
          memberCount: roomData.memberCount,
          isLocked: true // 모든 방은 비밀번호 있음
        });
      }
    });
    
    console.log(`검색 결과: ${searchResults.length}개`);
    return res.json(searchResults);
    
  } catch (error) {
    console.error('방 검색 오류:', error);
    return res.status(500).json([]);
  }
});

// 8. 학습 독촉 알림 (개별)
app.post('/api/studyrooms/notify/individual', authenticateUser, async (req, res) => {
  try {
    const { roomTitle, targetUserId } = req.body;
    const senderId = req.user.uid;
    
    console.log(`개별 학습 독촉: roomTitle=${roomTitle}, targetUserId=${targetUserId}`);
    
    // 방장 권한 확인
    const roomQuery = await db.collection('studyRooms')
      .where('title', '==', roomTitle)
      .where('ownerId', '==', senderId)
      .limit(1)
      .get();
    
    if (roomQuery.empty) {
      return res.status(403).json({ 
        success: false, 
        message: '권한이 없거나 존재하지 않는 방입니다.' 
      });
    }
    
    // 대상 사용자 찾기
    const targetFirebaseId = await findUserIdByNumericId(targetUserId);
    if (!targetFirebaseId) {
      return res.status(404).json({ 
        success: false, 
        message: '대상 사용자를 찾을 수 없습니다.' 
      });
    }
    
    // FCM 토큰 확인
    const targetUserDoc = await db.collection('users').doc(targetFirebaseId).get();
    if (!targetUserDoc.exists || !targetUserDoc.data().fcmToken) {
      return res.status(400).json({ 
        success: false, 
        message: '알림을 보낼 수 없습니다.' 
      });
    }
    
    const fcmToken = targetUserDoc.data().fcmToken;
    const senderInfo = await getUserInfo(senderId);
    
    // FCM 메시지 전송
    // 알림 문구는 여기서 바꾸는 것
    const message = {
      token: fcmToken,
      notification: {
        title: '📚 학습 독촉 알림',
        body: `${senderInfo.nickname} 방장님이 '${roomTitle}' 방에서 학습을 독촉했습니다!`
      },
      data: {
        type: 'STUDY_REMINDER',
        roomTitle: roomTitle,
        senderId: senderId
      },
      android: {
        priority: 'high',
        notification: {
          channelId: 'english_app_reminders_channel'
        }
      }
    };
    
    const response = await admin.messaging().send(message);
    console.log(`알림 전송 성공: ${response}`);
    
    return res.json({ 
      success: true, 
      message: '학습 독촉 알림을 발송했습니다.' 
    });
    
  } catch (error) {
    console.error('개별 알림 발송 오류:', error);
    return res.status(500).json({ 
      success: false, 
      message: '알림 발송 중 오류가 발생했습니다.' 
    });
  }
});

// 9. 학습 독촉 알림 (일괄)
app.post('/api/studyrooms/notify/batch', authenticateUser, async (req, res) => {
  try {
    const { roomTitle, targetUserIds } = req.body;
    const senderId = req.user.uid;
    
    console.log(`일괄 학습 독촉: roomTitle=${roomTitle}, 대상=${targetUserIds.length}명`);
    
    // 방장 권한 확인
    const roomQuery = await db.collection('studyRooms')
      .where('title', '==', roomTitle)
      .where('ownerId', '==', senderId)
      .limit(1)
      .get();
    
    if (roomQuery.empty) {
      return res.status(403).json({ 
        success: false, 
        message: '권한이 없거나 존재하지 않는 방입니다.' 
      });
    }
    
    const senderInfo = await getUserInfo(senderId);
    const messages = [];
    
    // 각 대상에 대한 메시지 준비
    for (const numericId of targetUserIds) {
      const targetFirebaseId = await findUserIdByNumericId(numericId);
      if (!targetFirebaseId) continue;
      
      const targetUserDoc = await db.collection('users').doc(targetFirebaseId).get();
      if (!targetUserDoc.exists || !targetUserDoc.data().fcmToken) continue;
      
      messages.push({
        token: targetUserDoc.data().fcmToken,
        notification: {
          title: '📚 학습 독촉 알림',
          body: `${senderInfo.nickname} 방장님이 '${roomTitle}' 방에서 학습을 독촉했습니다!`
        },
        data: {
          type: 'STUDY_REMINDER',
          roomTitle: roomTitle,
          senderId: senderId
        },
        android: {
          priority: 'high',
          notification: {
            channelId: 'english_app_reminders_channel'
          }
        }
      });
    }
    
    if (messages.length === 0) {
      return res.status(400).json({ 
        success: false, 
        message: '알림을 보낼 대상이 없습니다.' 
      });
    }
    
    // 일괄 전송
    const response = await admin.messaging().sendAll(messages);
    console.log(`일괄 알림 전송: 성공=${response.successCount}, 실패=${response.failureCount}`);
    
    return res.json({ 
      success: true, 
      message: `${response.successCount}명에게 알림을 발송했습니다.`,
      failureCount: response.failureCount
    });
    
  } catch (error) {
    console.error('일괄 알림 발송 오류:', error);
    return res.status(500).json({ 
      success: false, 
      message: '알림 발송 중 오류가 발생했습니다.' 
    });
  }
});

// Express 앱을 Firebase Function으로 export
exports.api = functions
  .region('asia-northeast3')
  .https.onRequest(app);