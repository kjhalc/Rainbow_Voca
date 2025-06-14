const admin = require("firebase-admin");
const db = admin.firestore();

// 앱의 전체 단어 수를 여기에 정의
const TOTAL_WORDS = 200; 
const LOG_PREFIX = "🚀 [MainPageCalculator]";

/**
 * 예상 완료일을 계산합니다. (Kotlin 로직 포팅)
 * @param {string} userId 사용자 ID
 * @param {number} dailyGoal 일일 학습 목표
 * @param {number} averageAccuracy 평균 정답률 (0.0 ~ 1.0)
 * @returns {Promise<string>} "YYYY년 MM월 DD일" 형식의 문자열
 */
async function calculateEstimatedCompletionDate(userId, dailyGoal, averageAccuracy = 0.8) {
  try {
    // 1. 사용자의 복습 단어 정보만 가져옵니다
    const reviewWordsSnapshot = await db.collection("users").doc(userId).collection("review_words").get();
    
    // 2. 미학습 단어 수를 계산합니다 (전체 단어 - 복습 단어)
    const unstudiedCount = TOTAL_WORDS - reviewWordsSnapshot.size;
    
    // 3. 현재 복습중인 단어들을 단계별로 집계합니다
    const stageCountMap = new Map();
    reviewWordsSnapshot.forEach(doc => {
      const stage = doc.data().stage || 0;
      stageCountMap.set(stage, (stageCountMap.get(stage) || 0) + 1);
    });

    // 4. 복잡한 계산을 수행합니다
    const daysToNextStage = { 0: 1, 1: 3, 2: 7, 3: 14, 4: 21, 5: 28, 6: 0 };
    const daysForUnstudied = (dailyGoal > 0) ? Math.ceil(unstudiedCount / dailyGoal) : 0;
    let maxDaysToMaster = daysForUnstudied;

    for (let stage = 0; stage <= 5; stage++) {
      const wordsInStage = stageCountMap.get(stage) || 0;
      if (wordsInStage > 0) {
        let daysFromStageToMaster = 0;
        for (let s = stage; s <= 5; s++) {
          daysFromStageToMaster += daysToNextStage[s];
        }
        const adjustedDays = Math.ceil(daysFromStageToMaster * (1 + (1 - averageAccuracy)));
        maxDaysToMaster = Math.max(maxDaysToMaster, adjustedDays);
      }
    }

    // 5. 계산된 날짜를 미래 날짜로 변환합니다
    const currentDate = new Date();
    currentDate.setDate(currentDate.getDate() + maxDaysToMaster);
    
    const year = currentDate.getFullYear();
    const month = String(currentDate.getMonth() + 1).padStart(2, '0');
    const day = String(currentDate.getDate()).padStart(2, '0');

    return `${year}년 ${month}월 ${day}일`;
  } catch (e) {
    console.error(`${LOG_PREFIX} [예상 완료일] 계산 실패:`, e);
    return "계산 중...";
  }
}

/**
 * 특정 사용자의 메인 페이지 데이터를 모두 재계산하고 Firestore에 업데이트합니다.
 * @param {string} userId - 데이터를 업데이트할 사용자의 ID
 * @returns {Promise<object|null>} 업데이트된 데이터 객체 또는 null
 */
async function updateMainPageDataForUser(userId) {
  try {
    console.log(`${LOG_PREFIX} 메인 페이지 데이터 재계산 시작: userId=${userId}`);

    const [userProfileSnapshot, reviewWordsSnapshot] = await Promise.all([
        db.collection("users").doc(userId).get(),
        db.collection("users").doc(userId).collection("review_words").get(),
    ]);

    if (!userProfileSnapshot.exists) {
      console.error(`${LOG_PREFIX} 사용자 문서를 찾을 수 없습니다: ${userId}`);
      return null;
    }
    const userProfile = userProfileSnapshot.data() || {};
    const now = new Date();
    
    // --- 👇 최종 계산 로직 ---
    
    const stageCounts = { red: 0, orange: 0, yellow: 0, green: 0, blue: 0, indigo: 0, violet: 0 };
    const colorMap = ["red", "orange", "yellow", "green", "blue", "indigo", "violet"];
    
    let wordsInSpacedRepetition = 0; // Stage 1~6에 있는 단어 수
    let dueToday = 0;

    reviewWordsSnapshot.forEach((doc) => {
        const { stage = 0, nextReviewAt, isMastered = false } = doc.data();
        
        // 마스터한 단어는 어떤 계산에도 포함하지 않습니다.
        if (isMastered) return;

        // Stage 1~6에 있는 단어 수 및 각 단계별 그래프 값을 계산합니다.
        if (stage >= 1 && stage <= 6) {
            wordsInSpacedRepetition++;
            stageCounts[colorMap[stage]]++;
        }
        
        // '오늘의 복습' 개수를 셉니다 (stage > 0 인 단어만).
        if (nextReviewAt && nextReviewAt.toDate() <= now && stage > 0) {
            dueToday++;
        }
    });

    // 1. ✨ 진도율: 오직 stage 1~6에 있는 단어들만으로 계산합니다.
    const progressRate = (wordsInSpacedRepetition / TOTAL_WORDS) * 100;
    
    // 2. ✨ 대기(red) 그래프: 전체 단어 수에서 stage 1~6에 있는 단어 수를 뺀 값으로 설정합니다.
    stageCounts.red = TOTAL_WORDS - wordsInSpacedRepetition;
    
    // --- 👆 최종 계산 로직 끝 ---

    const estimatedDate = await calculateEstimatedCompletionDate(userId, userProfile.dailyWordGoal || 10);

    const updatedData = {
        todayReviewCount: dueToday,
        stageCounts: stageCounts,
        progressRate: parseFloat(progressRate.toFixed(1)),
        estimatedCompletionDate: estimatedDate,
        nickname: userProfile.nickname || "사용자",
        email: userProfile.email || "",
        dailyWordGoal: userProfile.dailyWordGoal || 10,
        isTodayLearningComplete: userProfile.isTodayLearningComplete || false,
        isPostLearningReviewReady: userProfile.isPostLearningReviewReady || false,
        wordsForAiReadingToday: userProfile.wordsForAiReadingToday || [],
    };

    await db.collection("users").doc(userId).set(updatedData, { merge: true });
    console.log(`${LOG_PREFIX} 메인 페이지 데이터 업데이트 완료: userId=${userId}`);
    
    return updatedData;
  } catch (error) {
    console.error(`${LOG_PREFIX} 데이터 업데이트 중 오류 발생 (userId: ${userId}):`, error);
    return null;
  }
}

// 이 함수를 외부(index.js)에서 사용할 수 있도록 export 합니다.
module.exports = {
  updateMainPageDataForUser,
};