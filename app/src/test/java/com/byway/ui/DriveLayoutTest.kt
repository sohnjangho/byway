package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveLayoutTest {
    /** 저장값이 없거나 모르는 값이면 PM이 정한 주 배치(3분할)로 본다. */
    @Test
    fun unknownKeyIsSplit() {
        listOf(null, "", "x").forEach { assertEquals(DriveLayout.SPLIT, DriveLayout.of(it)) }
        assertEquals(DriveLayout.NAVI, DriveLayout.of("navi"))
        assertEquals(DriveLayout.SPLIT, DriveLayout.of("split"))
    }

    /** 앱은 화면을 직접 나눌 수 없다. 1/3 창이 아니면 사용자가 나누도록 안내한다. */
    @Test
    fun splitOutsideThirdAsksToSplit() {
        assertEquals(StartPlan.GUIDE, startPlan(DriveLayout.SPLIT, WindowClass.TWO))
        assertEquals(StartPlan.GUIDE, startPlan(DriveLayout.SPLIT, WindowClass.FULL))
    }

    @Test
    fun splitInThirdGoes() {
        assertEquals(StartPlan.GO, startPlan(DriveLayout.SPLIT, WindowClass.THIRD))
    }

    @Test
    fun naviAlwaysGoes() {
        WindowClass.entries.forEach { assertEquals(StartPlan.GO, startPlan(DriveLayout.NAVI, it)) }
    }
}
