package com.byway.ui.party

import com.byway.core.course.Course
import com.byway.core.share.PartyMember
import com.byway.core.share.SharedCourse
import com.byway.core.share.Visibility

/** 위치 공유 동의(공유 설계 6-5). 항목, 목적, 보유 기간, 거부 권리를 적는다. 동의 버튼만 동의로 본다. */
internal val LOCATION_CONSENT = listOf(
    "함께 달릴 친구에게 내 닉네임, 아바타, 차 위치를 보여 줍니다.",
    "위치는 함께 달리는 동안 2초마다 보내고, 서버는 받은 지 60초가 지나면 지웁니다.",
    "함께 달리기가 끝나거나 '위치 공유 끄기', '코스 끝내기'를 누르면 바로 멈추고 서버에서 지웁니다.",
    "동의하지 않을 수 있습니다. 그러면 함께 출발하지 않고 함께 달리기에서 나갑니다. 코스는 언제든 혼자 달릴 수 있습니다.",
)

internal const val STAR_NOTE = "별표하면 이 함께 달리기가 끝나도 내 닉네임과 아바타가 남습니다. 별표를 끄거나 나가면 지웁니다"

internal const val PARTY_INTRO = "여러 차가 함께 달립니다. 사람을 먼저 모으고, 리더가 코스를 고릅니다. 정차 중에만 합니다"

// 함께달리기 문구(함께달리기 설계 6-2~6-6). 위치가 나가는 버튼은 모두 "동의하고"로 시작한다.
internal const val INVITE_NOTE = "다른 차에서 함께달리기 → 코드로 참여에 이 코드를 넣으면 들어옵니다"
internal const val PICK_BUTTON = "코스 고르기"
internal const val START_BUTTON = "동의하고 출발"
internal const val ACCEPT_BUTTON = "동의하고 함께 달리기"
internal const val DECLINE_BUTTON = "거부하고 함께 달리기에서 나가기"
internal const val PASS_LEADER = "리더 넘기기"
internal const val KICK = "내보내기"
internal const val REOPEN_BUTTON = "다시 모이기"
internal const val ENTER_BUTTON = "열려 있음 · 들어가기"
internal const val LEFT_NOTE = "함께 달리기에서 나왔습니다"
internal const val NO_COURSE_YET = "리더가 아직 코스를 고르지 않았습니다"
internal val PICK_GROUPS = listOf("내가 저장한 코스", "목록 코스", "공유받은 코스")

/**
 * 리더가 고른 코스 id 로 기기의 코스를 찾는다. 내가 올린 공유본(s-)은 기기에 내 코스(m-)로만 있으므로 [uploads](m- → 서버 id)로 찾는다(검토 C-1).
 */
internal fun findPartyCourse(id: String, courses: List<Course>, mine: List<Course>, uploads: Map<String, String>): Course? =
    courses.firstOrNull { it.id == id } ?: mine.firstOrNull { uploads[it.id] == id }

/**
 * 파티 안 아래 칸의 버튼(앞이 주 버튼). 모두 나갈 수 있고 리더는 코스 고르기와 파티 끝내기도 있다(설계 6-3, 검토 I-4).
 * 리더의 코스가 끝나면 코스를 비우므로 "다시 출발"은 두지 않는다(PM 11-11).
 */
internal fun partyActions(owner: Boolean): List<String> =
    listOfNotNull(PICK_BUTTON.takeIf { owner }, LEAVE_BUTTON, END_BUTTON.takeIf { owner })

internal const val LEAVE_BUTTON = "나가기"
internal const val END_BUTTON = "함께 달리기 끝내기"

/** 질문 화면의 제목. 제안한 리더가 나가 내가 리더가 되었으면 내 이름 대신 묻기만 한다. */
internal fun askTitle(leaderName: String, iAmLeader: Boolean): String =
    if (iAmLeader) "이 코스로 함께 달릴까요?" else "${leaderName.ifEmpty { "리더" }}님이 코스를 골랐습니다"

/**
 * 멤버 줄의 작은 글. 리더와, 리더가 코스를 골랐으면([proposal] > 0) 그 코스에 대한 응답을 함께 적는다(함께달리기 설계 6-3).
 * 시연용 가상 멤버는 실제 사람처럼 보이지 않게 밝힌다.
 */
internal fun memberTags(member: PartyMember, proposal: Int): String = listOfNotNull(
    "리더".takeIf { member.leader },
    "나".takeIf { member.me },
    "위치 공유 중".takeIf { member.me && member.sharing },
    "시연용 가상 친구".takeIf { member.demo },
    (if (member.accepted) "함께 달림" else "응답 기다림").takeIf { proposal > 0 },
).joinToString(" · ")

/** 만들기·참여 요청에는 닉네임과 아바타가 실린다. 보내기 전에 알리고 동의 버튼으로 받는다. */
internal val PARTY_JOIN_CONSENT = listOf(
    "함께 달리기를 만들거나 참여하면 함께 달릴 친구에게 내 닉네임과 아바타가 보입니다.",
    "함께 달리기가 끝나거나 나가면 서버에서 지웁니다. 별표한 함께 달리기는 별표를 끌 때 지웁니다.",
)
/** 파티원 초대는 파티를 열며 내 닉네임과 아바타를 올린다. 그래서 동의 버튼이다. */
internal const val INVITE_BUTTON = "동의하고 함께 달릴 친구 초대"
internal const val JOIN_BUTTON = "동의하고 참여"

/** 코스 고르기의 한 줄. [upload]면 아직 공유하지 않은 내 코스라 고를 때 이 파티 범위로 올린다([id]는 기기의 m- id). */
internal data class CourseChoice(val id: String, val name: String, val upload: Boolean)

/** 코스 고르기의 세 묶음(함께달리기 설계 6-5): 내가 저장한 코스, 목록 코스, 공유받은 코스. */
internal data class CourseGroups(val mine: List<CourseChoice>, val listed: List<CourseChoice>, val received: List<CourseChoice>)

/**
 * 기기의 코스([courses])를 세 묶음으로 나눈다. [shared]는 서버에서 받은 코스의 범위, [uploads]는 내 코스(m-)를 올린 서버 id 다.
 * 내가 올린 공유본은 내 코스 줄로 고른다. 파티 범위 코스는 이 파티([partyCode])에 묶인 것만 받은 코스로 고르고, 다른 파티 것은 뺀다.
 */
internal fun partyCourseGroups(courses: List<Course>, shared: List<SharedCourse>, uploads: Map<String, String>, partyCode: String): CourseGroups {
    val byId = shared.associateBy { it.id }
    val mine = mutableListOf<CourseChoice>()
    val listed = mutableListOf<CourseChoice>()
    val received = mutableListOf<CourseChoice>()
    courses.forEach { course ->
        val info = byId[course.id]
        when {
            course.dummy -> Unit
            course.id.startsWith("m-") -> mine += uploads[course.id]?.let { CourseChoice(it, course.name, upload = false) } ?: CourseChoice(course.id, course.name, upload = true)
            info == null -> listed += CourseChoice(course.id, course.name, upload = false)
            info.mine -> Unit
            info.visibility == Visibility.PUBLIC -> listed += CourseChoice(course.id, course.name, upload = false)
            info.visibility == Visibility.LINK -> received += CourseChoice(course.id, course.name, upload = false)
            info.visibility == Visibility.PARTY && info.partyCode == partyCode -> received += CourseChoice(course.id, course.name, upload = false)
        }
    }
    return CourseGroups(mine, listed, received)
}
