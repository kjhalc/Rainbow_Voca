//package com.example.englishapp.data.repository
//
//import com.example.englishapp.model.StudyMemberProfile
//import com.example.englishapp.model.StudyRoom
//
//object DummyStudyRoomProvider {
//
//    private var nextId = 1000 // ID 중복 방지를 위해 더 큰 수에서 시작
//
//    private fun genId(): Int {
//        return nextId++
//    }
//
//    fun getPredefinedRooms(
//        currentUserNickname: String = "레인",
//        currentUserId: Int = 101 // 현재 사용자의 ID도 받아옴
//    ): MutableMap<String, StudyRoom> {
//        nextId = currentUserId + 100 // 현재 사용자 ID 기반으로 오프셋 조정
//        val roomsMap = mutableMapOf<String, StudyRoom>()
//
//        val adminProfile = "admin_profile"
//        val userLogo = "logo"
//        val profilePic1 = "profile1"
//        val profilePic2 = "profile2"
//        val profilePic3 = "profile3"
//
//        // 방 1
//        val owner1 = StudyMemberProfile(userId = currentUserId, nickname = currentUserNickname, profileImage = userLogo, isAttendedToday = true, totalWordCount = 150, studiedWordCount = 70, wrongAnswerCount = 5)
//        val member1Room1 = StudyMemberProfile(userId = genId(), nickname = "알파테스터", profileImage = profilePic1, isAttendedToday = true, totalWordCount = 100, studiedWordCount = 30, wrongAnswerCount = 2)
//        val member2Room1 = StudyMemberProfile(userId = genId(), nickname = "베타테스터", profileImage = profilePic2, isAttendedToday = false, totalWordCount = 80, studiedWordCount = 10, wrongAnswerCount = 0)
//        val membersRoom1 = mutableListOf(owner1, member1Room1, member2Room1)
//        roomsMap["나의 영어 단어 그룹"] = StudyRoom(
//            title = "나의 영어 단어 그룹",
//            password = "password123",
//            ownerNickname = owner1.nickname,
//            ownerId = owner1.userId, // ownerId 전달
//            members = membersRoom1,
//            isAdminForCurrentUser = (owner1.userId == currentUserId), // 현재 사용자가 방장인지 여부
//            memberCount = membersRoom1.size
//        )
//
//        // 방 2
//        val owner2 = StudyMemberProfile(userId = genId(), nickname = "영어고수", profileImage = adminProfile, isAttendedToday = true, wrongAnswerCount = 1)
//        val member1Room2 = StudyMemberProfile(userId = genId(), nickname = "열공이", profileImage = profilePic3, isAttendedToday = false, wrongAnswerCount = 0)
//        val membersRoom2 = mutableListOf(owner2, member1Room2)
//        roomsMap["매일 단어 도전방"] = StudyRoom(
//            title = "매일 단어 도전방",
//            password = "voca",
//            ownerNickname = owner2.nickname,
//            ownerId = owner2.userId, // ownerId 전달
//            members = membersRoom2,
//            isAdminForCurrentUser = (owner2.userId == currentUserId),
//            memberCount = membersRoom2.size
//        )
//
//        // 방 3
//        val owner3 = StudyMemberProfile(userId = genId(), nickname = "단어마법사", profileImage = profilePic1, isAttendedToday = true, wrongAnswerCount = 3)
//        val member1Room3 = StudyMemberProfile(userId = genId(), nickname = "수강생A", profileImage = profilePic2, isAttendedToday = true, wrongAnswerCount = 1)
//        val membersRoom3 = mutableListOf(owner3, member1Room3)
//        roomsMap["고급 어휘 마스터반"] = StudyRoom(
//            title = "고급 어휘 마스터반",
//            password = "wizard",
//            ownerNickname = owner3.nickname,
//            ownerId = owner3.userId, // ownerId 전달
//            members = membersRoom3,
//            isAdminForCurrentUser = (owner3.userId == currentUserId),
//            memberCount = membersRoom3.size
//        )
//        return roomsMap
//    }
//
//    fun getInitialJoinedRooms(
//        allRooms: Map<String, StudyRoom>,
//        targetId: Int
//    ): List<StudyRoom> {
//        return allRooms.values.filter { room ->
//            room.members.any { it.userId == targetId }
//        }
//    }
//}