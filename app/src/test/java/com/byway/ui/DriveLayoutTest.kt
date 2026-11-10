package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private val request = StartRequest("c1", 3)

    /** 사용자가 화면을 나눠 에움길이 1/3 칸이 되면 기다리던 출발을 꺼내고 안내를 닫는다. */
    @Test
    fun guideStartsInThirdWhileParked() {
        val (nav, got) = takeGuide(Nav(splitGuide = request), WindowClass.THIRD, parked = true)
        assertEquals(request, got)
        assertNull(nav.splitGuide)
    }

    @Test
    fun guideWaitsInWideWindow() {
        val before = Nav(splitGuide = request)
        val (nav, got) = takeGuide(before, WindowClass.TWO, parked = true)
        assertNull(got)
        assertEquals(before, nav)
    }

    /** 안내 중 기어를 D로 바꾼 뒤 나눠도 주행 중에는 출발하지 않는다. */
    @Test
    fun guideWaitsWhileMoving() {
        assertNull(takeGuide(Nav(splitGuide = request), WindowClass.THIRD, parked = false).second)
    }

    /** 나눈 뒤 폭 변화가 두 번 들어와도 한 번만 출발한다. */
    @Test
    fun guideIsTakenOnce() {
        val (nav, _) = takeGuide(Nav(splitGuide = request), WindowClass.THIRD, parked = true)
        assertNull(takeGuide(nav, WindowClass.THIRD, parked = true).second)
    }
}
