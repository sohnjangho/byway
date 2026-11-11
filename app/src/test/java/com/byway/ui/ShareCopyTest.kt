package com.byway.ui

import com.byway.core.share.SharedCourse
import com.byway.service.DriveState
import com.byway.service.sameShared
import com.byway.ui.share.PARTY_NEEDED
import com.byway.ui.share.SHARE_SCOPES
import com.byway.ui.share.consentLines
import com.byway.ui.share.finishesText
import com.byway.ui.share.scopeHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCopyTest {
    @Test
    fun threeScopesInOrder() {
        assertEquals(listOf("public" to "전체", "party" to "파티", "link" to "링크"), SHARE_SCOPES)
        assertTrue("둘러보기" in scopeHint("public"))
        assertTrue("멤버" in scopeHint("party"))
        assertTrue("코드" in scopeHint("link"))
    }

    @Test
    fun consentSaysWhatGoesWhoSeesAndWhenItIsDeleted() {
        SHARE_SCOPES.forEach { (scope, _) ->
            val text = consentLines(scope).joinToString(" ")
            assertTrue(scope, "경로" in text)
            assertTrue(scope, "닉네임" in text)
            assertTrue(scope, "지웁니다" in text)
            assertTrue(scope, scopeHint(scope) in text)
        }
    }

    @Test
    fun finishesAreCounted() {
        assertEquals("아직 완주 기록 없음", finishesText(0))
        assertEquals("완주 3회", finishesText(3))
    }

    @Test
    fun shareSheetOnlyWhenParked() {
        assertEquals(Screen.Share("m-abc"), screenFor(DriveState(parked = true), Nav(tab = Tab.MINE, share = "m-abc")))
        assertEquals(Screen.Waiting, screenFor(DriveState(parked = false), Nav(tab = Tab.MINE, share = "m-abc")))
        assertFalse(Nav(share = "m-abc").toTab(Tab.HOME).share != null)
    }

    @Test
    fun sharedListChangesOnlyWhenSomethingShown() {
        val a = SharedCourse("s-1", "ABCDEF", "public", 0, false, "{}")
        assertTrue(sameShared(listOf(a), listOf(a.copy())))
        assertFalse(sameShared(listOf(a), listOf(a.copy(finishes = 1))))
        assertFalse(sameShared(listOf(a), emptyList()))
    }

    /** 파티는 함께달리기 메뉴에서 만든다. 없앤 내 코스의 파티 버튼을 가리키지 않는다(검토 M-5). */
    @Test
    fun partyScopeSendsYouToTogether() {
        assertTrue("함께달리기" in PARTY_NEEDED)
    }
}
