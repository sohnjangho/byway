package com.byway.ui

import com.byway.ui.party.LOCATION_CONSENT
import com.byway.ui.party.STAR_NOTE
import com.byway.ui.party.memberTags
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
