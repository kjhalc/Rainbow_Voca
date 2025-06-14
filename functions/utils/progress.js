// functions/utils/progress.js
// 학습 통계 계산 – 모든 Cloud Function에서 공통 사용

const admin = require('firebase-admin');
const db = admin.firestore();

const TOTAL_WORDS = 200;   // 서비스 전역 고정값
const MAX_STAGE   = 6;     // stage 0~6, 총 7단계

exports.calculateUserProgress = async (uid) => {
  // 1. 복습용 단어(1~6단계) 문서 가져오기
  const reviewSnap = await db
    .collection('users').doc(uid)
    .collection('review_words')
    .where('stage', '>=', 1).where('stage', '<=', MAX_STAGE)
    .get();

  const studied = reviewSnap.size;               // 학습 완료 단어 수

  // 2. 단계별 분포 초기화 및 집계
  const dist = {};
  for (let i = 0; i <= MAX_STAGE; i++) dist[`stage${i}`] = 0;
  reviewSnap.forEach(doc => {
    const s = doc.data().stage;
    if (s >= 1 && s <= MAX_STAGE) dist[`stage${s}`]++;
  });
  dist.stage0 = Math.max(0, TOTAL_WORDS - studied);

  // 3. 오늘 틀린 단어(individual_states) 개수
  const wrongAgg = await db
    .collection('users').doc(uid)
    .collection('individual_states')
    .count().get();
  const todayWrongCount = wrongAgg.data().count || 0;

  // 4. 진도율 계산
  const progressRate = Math.round((studied / TOTAL_WORDS) * 100);

  return { progressRate, todayWrongCount, stageDistribution: dist };
};