정리)
파이어베이스(서버이자 데이터베이스)
-> 조선용 구글 계정 사용


하이브리드 구조
안드로이드 스튜디오 + 백엔드 (Node.js + Express) + Firebase

- 백엔드 API 이용(보안 중요, 여러 데이터 조합, 서버간 통신 등)
스터디룸 관리, Push 알림

- 안드로이드와 Firebase 직접 통신(실시간 데이터 동기화, 특정 사용자에게 종속된 데이터)
메인 대시보드 업데이트, 단어 상태관리(퀴즈, 학습, 갯수 설정 등등), 프로필 설정, 전체 단어불러오기

모든게 API 구조는 아님 -> firebase에서 컬렉션 건들면 바로바로 반영 가능

테스트 구조)
10분후 복습 
-> 원래는 10분후 바뀌어야 하지만 그냥 바로 바뀌는 구조로 작동

누적 복습 
-> 원래 1일, 3일 --- 있지만 지금은 무조건 1분 뒤로 설정 -> 자정에 초기화 되는 구조(실제 시나리오에 맞게) -> 이건 실제 백엔드라 구글 콘솔 function에서 "updateAllUsersDataAtMidnight" 함수를 직접 실행해야 반영이되는 모습 
-> 누적 복습 알림도 매일 오전 9시에 반영해놨는데 "sendDailyReminder" 직접 테스트

진도율 관련
-> (메인이든, 관리자 모드든 progressRate 값을 바꾸는게 아니라 단어stage 카운트에 값을 바꾸면 반영)


꾸밈관련)
앱 로고
-> 앱이 폰에 깔려있을때의 모습 + 앱 시작할때 보이는 로고 
androidmainfest.xml
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"

이 부분이 문제인듯?

퀴즈)
->  WordQuizActivity.kt의 setupObservers() 함수 Toast.makeText(this, feedback, Toast.LENGTH_SHORT).show() 지금 이 상태 xml(이미지뷰?) 같은거 만들어서

private fun setupObservers() 
viewModel.showAnswerFeedback.observe(this) { feedbackPair ->
            feedbackPair?.let { (isCorrect, feedback) ->이 함수에서 정/오답에 따라 설정하면 될듯


스터디룸)
랭킹 시스템 일단 이름 옆에 걍 동그라미 띄우는걸로 처리해둠


AI독해)
메인화면에서 android:id="@+id/btn_AI" 이버튼 눌러서 이동하게
passage.xml, passage.activity로 이동하게 짜고 뷰모델이나 repository 로직 짜서 사용하면 될듯

쓸 단어는 users의 wordsForAiReadingToday에 배열로 저장되어있는 단어 가져와서 사용
- wordsForAiReadingToday 이 배열 updateAllUsersDataAtMidnight 이 함수 작동하면 초기화

