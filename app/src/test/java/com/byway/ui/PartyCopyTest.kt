package com.byway.ui

import com.byway.ui.party.LOCATION_CONSENT
import com.byway.ui.party.STAR_NOTE
import com.byway.ui.party.memberTags
import com.byway.ui.party.PARTY_JOIN_CONSENT
import com.byway.ui.party.ACCEPT_BUTTON
import com.byway.ui.party.INVITE_BUTTON
import com.byway.ui.party.START_BUTTON
import com.byway.ui.party.askTitle
import com.byway.ui.party.findPartyCourse
import com.byway.ui.party.partyActions
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
        listOf("닉네임", "아바타", "위치", "60초", "지웁니다", "동의하지 않을 수").forEach { assertTrue(it, it in text) }
        // 동의 없이 함께 달리는 길은 없다. 거부의 결과를 그대로 적는다(검토 I-3).
        assertTrue(LOCATION_CONSENT.none { "동의하지 않아도 파티 코스는" in it })
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

    /** 더미 코스는 서버에 없어 상대 기기가 못 받을 수 있다. 코스 고르기에 넣지 않는다(더 많은 코스 설계 8절). */
    @Test
    fun dummiesAreNotPartyChoices() {
        val dummy = com.byway.core.course.Course(
            "dummy-1-01", "더미 길", listOf(com.byway.core.geo.GeoPoint(37.0, 127.0), com.byway.core.geo.GeoPoint(37.0, 127.1)),
            listOf(com.byway.core.course.Section("끝", 0, 1)), dummy = true, declaredMeters = 12_000.0,
        )
        val groups = partyCourseGroups(listOf(dummy), emptyList(), emptyMap(), "ABC234")
        assertTrue((groups.mine + groups.listed + groups.received).none { it.id == dummy.id })
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
        listOf(START_BUTTON, ACCEPT_BUTTON).forEach { assertTrue(it, it.startsWith("동의하고")) }
    }

    /** 제안한 사람이 나간 뒤 내가 리더가 되었으면 내 이름을 적지 않는다(함께달리기 설계 6-10.4). */
    @Test
    fun askTitleNamesTheLeader() {
        assertEquals("둘님이 코스를 골랐습니다", askTitle("둘", iAmLeader = false))
        assertEquals("리더님이 코스를 골랐습니다", askTitle("", iAmLeader = false))
        assertEquals("이 코스로 함께 달릴까요?", askTitle("하나", iAmLeader = true))
    }

    /** 내가 올린 공유본(s-)은 기기에 내 코스(m-)로만 있다. 리더가 그 id 로 골라도 내 코스로 찾는다(검토 C-1). */
    @Test
    fun myOwnUploadIsFoundAsTheMineCourse() {
        fun course(id: String) = com.byway.core.course.Course(id, id, listOf(com.byway.core.geo.GeoPoint(37.0, 127.0), com.byway.core.geo.GeoPoint(37.0, 127.1)), listOf(com.byway.core.course.Section("1구간", 0, 1)))
        val listed = listOf(course("paldang-sunset"))
        val mine = listOf(course("m-1"))
        assertEquals("m-1", findPartyCourse("s-9", listed, mine, mapOf("m-1" to "s-9"))?.id)
        assertEquals("paldang-sunset", findPartyCourse("paldang-sunset", listed, mine, emptyMap())?.id)
        assertEquals(null, findPartyCourse("s-404", listed, mine, emptyMap()))
    }

    /** 리더도 나갈 수 있다(설계 6-3). 코스가 끝나면 비우므로 "다시 출발"은 없다(PM 11-11). */
    @Test
    fun everyoneCanLeaveAndTheLeaderCanAlsoEnd() {
        assertEquals(listOf("코스 고르기", "나가기", "파티 끝내기"), partyActions(owner = true))
        assertEquals(listOf("나가기"), partyActions(owner = false))
    }
}
