package com.byway.ui.party

import com.byway.core.course.Course
import com.byway.core.share.PartyMember
import com.byway.core.share.SharedCourse
import com.byway.core.share.Visibility

/** 위치 공유 동의(공유 설계 6-5). 항목, 목적, 보유 기간, 거부 권리를 적는다. 동의 버튼만 동의로 본다. */
internal val LOCATION_CONSENT = listOf(
    "같은 파티 멤버에게 내 닉네임, 아바타, 차 위치를 보여 줍니다.",
    "위치는 함께 달리는 동안 2초마다 보내고, 서버는 받은 지 60초가 지나면 지웁니다.",
    "파티가 끝나거나 '위치 공유 끄기', '코스 끝내기'를 누르면 바로 멈추고 서버에서 지웁니다.",
    "동의하지 않아도 파티 코스는 달릴 수 있습니다.",
)

internal const val STAR_NOTE = "별표하면 이 파티가 끝나도 내 닉네임과 아바타가 이 파티에 남습니다. 별표를 끄거나 파티를 나가면 지웁니다"

internal const val PARTY_INTRO = "같은 코스를 여러 차가 함께 달립니다. 만들기와 참여는 정차 중에만 합니다"

/**
 * 멤버 줄의 작은 글. 리더와, 리더가 코스를 골랐으면([proposal] > 0) 그 코스에 대한 응답을 함께 적는다(함께달리기 설계 6-3).
 * 시연용 가상 멤버는 실제 사람처럼 보이지 않게 밝힌다.
 */
internal fun memberTags(member: PartyMember, proposal: Int): String = listOfNotNull(
    "리더".takeIf { member.leader },
    "나".takeIf { member.me },
    "위치 공유 중".takeIf { member.me && member.sharing },
    "시연용 가상 멤버".takeIf { member.demo },
    (if (member.accepted) "함께 달림" else "응답 기다림").takeIf { proposal > 0 },
).joinToString(" · ")

/** 만들기·참여 요청에는 닉네임과 아바타가 실린다. 보내기 전에 알리고 동의 버튼으로 받는다. */
internal val PARTY_JOIN_CONSENT = listOf(
    "파티를 만들거나 참여하면 같은 파티 멤버에게 내 닉네임과 아바타가 보입니다.",
    "파티가 끝나거나 파티를 나가면 서버에서 지웁니다. 별표한 파티는 별표를 끌 때 지웁니다.",
)
/** 파티원 초대는 파티를 열며 내 닉네임과 아바타를 올린다. 그래서 동의 버튼이다. */
internal const val INVITE_BUTTON = "동의하고 파티원 초대"
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
