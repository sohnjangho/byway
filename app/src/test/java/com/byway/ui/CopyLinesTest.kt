package com.byway.ui

import com.byway.ui.party.INVITE_NOTE
import com.byway.ui.party.LOCATION_CONSENT
import com.byway.ui.party.PARTY_INTRO
import com.byway.ui.party.PARTY_JOIN_CONSENT
import com.byway.ui.party.STAR_NOTE
import com.byway.ui.party.bullet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PM 11-11: 같은 맥락의 문구는 되도록 한 줄에 둔다. 두 줄이면 의미가 이어지는 자리에서 직접 나눈다.
 * 칸 폭에 맞춘 글자 수(2/3 창 기준, 실측)를 줄마다 넘지 않는지 본다. 넘으면 칸이 아무 데서나 줄을 바꾼다.
 */
class CopyLinesTest {
    private fun assertLines(limit: Int, texts: List<String>) = texts.forEach { text ->
        text.split("\n").forEach { line -> assertTrue("$limit 자 넘음: $line", line.length <= limit) }
    }

    @Test
    fun togetherCopyFitsItsColumn() {
        assertLines(30, listOf(PARTY_INTRO, INVITE_NOTE) + PARTY_JOIN_CONSENT.map(::bullet))
        assertLines(22, listOf(STAR_NOTE))
        assertLines(38, LOCATION_CONSENT.map(::bullet))
    }

    /** 위치 동의 넷째 줄은 PM이 지금 문구 그대로 쓰기로 했다(11-11). 낱말은 그대로 두고 문장 사이에서만 줄을 나눈다. */
    @Test
    fun pmConsentLineKeepsItsWords() {
        val text = LOCATION_CONSENT.joinToString(" ").replace("\n", " ")
        assertTrue("동의하지 않을 수 있습니다. 그러면 함께 출발하지 않고 함께 달리기에서 나갑니다. 코스는 언제든 혼자 달릴 수 있습니다." in text)
    }

    @Test
    fun chooserNotesFitTheirCards() {
        DriveLayout.entries.forEach { layout ->
            assertLines(24, listOf(choiceNote(layout)))
            assertTrue(choiceNote(layout).split("\n").size <= 2)
        }
        assertLines(30, listOf(DRIVE_LAYOUT_NOTE))
    }

    /** 설정 칩은 짧은 이름이라 셋이 한 줄에 다 보인다. 출발 고르기 그림에는 긴 이름(칸 구성)을 쓴다. */
    @Test
    fun settingsChipsAreShort() {
        assertEquals(listOf("3분할 · 내비 가운데", "3분할 · 에움길 가운데", "2분할 · 내비 넓게"), DriveLayout.entries.map { it.short })
    }

    @Test
    fun bulletIndentsTheNextLine() {
        assertEquals("· 가\n  나", bullet("가\n나"))
    }
}
