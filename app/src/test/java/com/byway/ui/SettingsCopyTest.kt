package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsCopyTest {
    /** 대화상자 없이 두 번 눌러야 지워진다. 첫 번째는 되묻는 문구다. 버튼 반 칸에 한 줄로 들어가야 한다. */
    @Test
    fun clearingRecordsAsksOnceBeforeItHappens() {
        assertEquals("내 기록 지우기", clearLabel(0))
        assertEquals("다시 눌러 지우기", clearLabel(1))
        assertEquals("지웠습니다", clearLabel(2))
    }

    @Test
    fun everyLabelFitsTheHalfWidthButton() {
        assertEquals(emptyList<String>(), listOf(0, 1, 2).map { clearLabel(it) }.filter { it.length > clearLabel(0).length + 1 })
    }

    @Test
    fun onlyTheSecondPressClears() {
        assertEquals(listOf(false, true, false), listOf(0, 1, 2).map { clearsOnPress(it) })
    }

    @Test
    fun theLabelNeverGoesBackAfterClearing() {
        assertEquals(2, nextClearStep(2))
        assertNotEquals(clearLabel(0), clearLabel(nextClearStep(1)))
    }
}
