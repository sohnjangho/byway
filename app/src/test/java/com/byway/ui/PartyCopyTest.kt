package com.byway.ui

import com.byway.ui.party.LOCATION_CONSENT
import com.byway.ui.party.STAR_NOTE
import com.byway.ui.party.memberTags
import com.byway.ui.party.PARTY_JOIN_CONSENT
import com.byway.ui.party.CREATE_BUTTON
import com.byway.ui.party.JOIN_BUTTON
import com.byway.ui.party.partyCourseChoices
import com.byway.core.share.PartyMember
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
        assertTrue(CREATE_BUTTON.startsWith("동의하고") && JOIN_BUTTON.startsWith("동의하고"))
    }

    /** 내가 만든 코스는 공유한 뒤 서버 id 로 고를 수 있다. 남의 파티 코스와 공유 안 한 내 코스는 뺀다. */
    @Test
    fun partyCoursesIncludeMySharedOnes() {
        fun course(id: String, name: String) = com.byway.core.course.Course(id, name, listOf(com.byway.core.geo.GeoPoint(37.0, 127.0), com.byway.core.geo.GeoPoint(37.0, 127.1)), listOf(com.byway.core.course.Section("1구간", 0, 1)))
        val choices = partyCourseChoices(
            courses = listOf(course("paldang-sunset", "팔당 노을"), course("m-mine", "내 길"), course("m-local", "공유 안 한 길"), course("s-other", "남의 파티 코스")),
            hidden = setOf("s-other"),
            uploads = mapOf("m-mine" to "s-mine"),
        )
        assertEquals(listOf("paldang-sunset" to "팔당 노을", "s-mine" to "내 길"), choices)
    }

    @Test
    fun starNoteSaysWhatStaysAndHowToRemoveIt() {
        assertTrue("남습니다" in STAR_NOTE && "지웁니다" in STAR_NOTE)
    }

    @Test
    fun demoMembersAreLabelled() {
        val demo = PartyMember("demo-1", "시연용 1", "avatar-09", demo = true, me = false, sharing = true, starred = false)
        val me = PartyMember("m-1", "하나", "avatar-01", demo = false, me = true, sharing = true, starred = false)
        assertEquals("시연용 가상 멤버", memberTags(demo))
        assertEquals("나 · 위치 공유 중", memberTags(me))
        assertEquals("", memberTags(me.copy(me = false, sharing = false)))
    }
}
