package com.byway.ui.party

import com.byway.core.course.Course
import com.byway.core.share.PartyMember

/** 위치 공유 동의(공유 설계 6-5). 항목, 목적, 보유 기간, 거부 권리를 적는다. 동의 버튼만 동의로 본다. */
internal val LOCATION_CONSENT = listOf(
    "같은 파티 멤버에게 내 닉네임, 아바타, 차 위치를 보여 줍니다.",
    "위치는 함께 달리는 동안 2초마다 보내고, 서버는 받은 지 60초가 지나면 지웁니다.",
    "파티가 끝나거나 '위치 공유 끄기', '코스 끝내기'를 누르면 바로 멈추고 서버에서 지웁니다.",
    "동의하지 않아도 파티 코스는 달릴 수 있습니다.",
)

internal const val STAR_NOTE = "별표하면 이 파티가 끝나도 내 닉네임과 아바타가 이 파티에 남습니다. 별표를 끄거나 파티를 나가면 지웁니다"

internal const val PARTY_INTRO = "같은 코스를 여러 차가 함께 달립니다. 만들기와 참여는 정차 중에만 합니다"

/** 멤버 줄의 작은 글. 시연용 가상 멤버는 실제 사람처럼 보이지 않게 밝힌다. */
internal fun memberTags(member: PartyMember): String = when {
    member.demo -> "시연용 가상 멤버"
    else -> listOfNotNull("나".takeIf { member.me }, "위치 공유 중".takeIf { member.me && member.sharing }).joinToString(" · ")
}

/** 만들기·참여 요청에는 닉네임과 아바타가 실린다. 보내기 전에 알리고 동의 버튼으로 받는다. */
internal val PARTY_JOIN_CONSENT = listOf(
    "파티를 만들거나 참여하면 같은 파티 멤버에게 내 닉네임과 아바타가 보입니다.",
    "파티가 끝나거나 파티를 나가면 서버에서 지웁니다. 별표한 파티는 별표를 끌 때 지웁니다.",
)
internal const val CREATE_BUTTON = "동의하고 이 코스로 파티 만들기"
internal const val JOIN_BUTTON = "동의하고 참여"

/**
 * 파티 코스로 고를 수 있는 것(id, 이름). 내가 만든 코스는 공유한 뒤 서버 id 로 고른다.
 * 공유하지 않은 내 코스와 다른 파티에서 받은 파티 코스([hidden])는 뺀다.
 */
internal fun partyCourseChoices(courses: List<Course>, hidden: Set<String>, uploads: Map<String, String>): List<Pair<String, String>> =
    courses.mapNotNull { course ->
        when {
            course.id.startsWith("m-") -> uploads[course.id]?.let { it to course.name }
            course.id in hidden -> null
            else -> course.id to course.name
        }
    }
