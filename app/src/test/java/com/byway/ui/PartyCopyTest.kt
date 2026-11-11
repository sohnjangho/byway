package com.byway.ui

import com.byway.ui.party.LOCATION_CONSENT
import com.byway.ui.party.STAR_NOTE
import com.byway.ui.party.memberTags
import com.byway.ui.party.PARTY_JOIN_CONSENT
import com.byway.ui.party.ACCEPT_BUTTON
import com.byway.ui.party.INVITE_BUTTON
import com.byway.ui.party.RESTART_BUTTON
import com.byway.ui.party.START_BUTTON
import com.byway.ui.party.askTitle
import com.byway.ui.party.JOIN_BUTTON
import com.byway.ui.party.CourseChoice
import com.byway.ui.party.partyCourseGroups
import com.byway.core.share.PartyMember
import com.byway.core.share.SharedCourse
import com.byway.core.share.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyCopyTest {
    /** 동의 화면: 항목, 목적, 보유 기간, 거부 권리(Pleos 심사 기준). */
    @Test
    fun locationConsentNamesItemsTimeAndRefusal() {
        val text = LOCATION_CONSENT.joinToString(" ")
        listOf("닉네임", "아바타", "위치", "60초", "지웁니다", "동의하지 않아도").forEach { assertTrue(it, it in text) }
    }

    /** 만들기·참여 요청에 닉네임과 아바타가 실리므로 그 전에 알리고 동의 버튼으로 받는다. */
    @Test
    fun joiningAsksBeforeNicknameAndAvatarLeave() {
        val text = PARTY_JOIN_CONSENT.joinToString(" ")
        listOf("닉네임", "아바타", "멤버", "지웁니다").forEach { assertTrue(it, it in text) }
        assertTrue(INVITE_BUTTON == "동의하고 파티원 초대" && JOIN_BUTTON.startsWith("동의하고"))
    }

    /**
     * 코스 고르기는 세 묶음이다(함께달리기 설계 6-5). 공유 안 한 내 코스는 고르면 이 파티로 올린다.
     * 이 파티에 묶인 파티 코스는 받은 코스로 고르고, 다른 파티에 묶인 남의 코스는 뺀다(설계 6-10.3).
     */
    @Test
    fun coursesSplitIntoThreeGroups() {
        fun course(id: String, name: String) = com.byway.core.course.Course(id, name, listOf(com.byway.core.geo.GeoPoint(37.0, 127.0), com.byway.core.geo.GeoPoint(37.0, 127.1)), listOf(com.byway.core.course.Section("1구간", 0, 1)))
        fun shared(id: String, visibility: String, mine: Boolean = false, partyCode: String? = null) =
            SharedCourse(id, null, visibility, finishes = 0, mine = mine, body = "", partyCode = partyCode)
        val groups = partyCourseGroups(
            courses = listOf(
                course("paldang-sunset", "팔당 노을"), course("m-1", "내 길"), course("m-2", "공유 안 한 길"),
                course("s1", "공개 길"), course("s2", "링크 길"), course("s3", "이 파티 길"), course("s4", "남의 파티 길"), course("s-m1", "내 길"),
            ),
            shared = listOf(
                shared("s1", Visibility.PUBLIC), shared("s2", Visibility.LINK), shared("s3", Visibility.PARTY, partyCode = "ABC234"),
                shared("s4", Visibility.PARTY, partyCode = "ZZZ999"), shared("s-m1", Visibility.PUBLIC, mine = true),
            ),
            uploads = mapOf("m-1" to "s-m1"),
            partyCode = "ABC234",
        )
        assertEquals(listOf(CourseChoice("s-m1", "내 길", upload = false), CourseChoice("m-2", "공유 안 한 길", upload = true)), groups.mine)
        assertEquals(listOf("paldang-sunset", "s1"), groups.listed.map { it.id })
        assertEquals(listOf("s2", "s3"), groups.received.map { it.id })
    }

    @Test
    fun starNoteSaysWhatStaysAndHowToRemoveIt() {
        assertTrue("남습니다" in STAR_NOTE && "지웁니다" in STAR_NOTE)
    }

    @Test
    fun demoMembersAreLabelled() {
        val demo = PartyMember("demo-1", "시연용 1", "avatar-09", demo = true, me = false, sharing = true, starred = false)
        val me = PartyMember("m-1", "하나", "avatar-01", demo = false, me = true, sharing = true, starred = false)
        assertEquals("시연용 가상 멤버", memberTags(demo, proposal = 0))
        assertEquals("나 · 위치 공유 중", memberTags(me, proposal = 0))
        assertEquals("", memberTags(me.copy(me = false, sharing = false), proposal = 0))
    }

    /** 리더와 응답 상태를 목록에서 바로 본다(함께달리기 설계 6-3). */
    @Test
    fun tagsShowLeaderMeDemoAndAnswer() {
        val me = PartyMember("m-1", "하나", "avatar-01", demo = false, me = true, sharing = false, starred = false, leader = true, accepted = true)
        val demo = PartyMember("demo-1", "시연용 1", "avatar-09", demo = true, me = false, sharing = true, starred = false, accepted = true)
        val other = PartyMember("m-2", "둘", "avatar-02", demo = false, me = false, sharing = false, starred = false)
        assertEquals("리더 · 나 · 함께 달림", memberTags(me, proposal = 1))
        assertEquals("시연용 가상 멤버 · 함께 달림", memberTags(demo, proposal = 1))
        assertEquals("응답 기다림", memberTags(other, proposal = 1))
        assertEquals("", memberTags(other, proposal = 0))
    }

    /** 위치가 나가기 시작하는 버튼은 동의 버튼이다(함께달리기 설계 6-7). */
    @Test
    fun buttonsThatShareLocationSayAgree() {
        listOf(START_BUTTON, RESTART_BUTTON, ACCEPT_BUTTON).forEach { assertTrue(it, it.startsWith("동의하고")) }
    }

    /** 제안한 사람이 나간 뒤 내가 리더가 되었으면 내 이름을 적지 않는다(함께달리기 설계 6-10.4). */
    @Test
    fun askTitleNamesTheLeader() {
        assertEquals("둘님이 코스를 골랐습니다", askTitle("둘", iAmLeader = false))
        assertEquals("리더님이 코스를 골랐습니다", askTitle("", iAmLeader = false))
        assertEquals("이 코스로 함께 달릴까요?", askTitle("하나", iAmLeader = true))
    }
}
