package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLayoutTest {
    /** 저장값이 없거나 모르는 값이면 PM이 정한 주 배치(3분할, 내비 가운데)로 본다. 예전 저장값도 그대로 읽힌다. */
    @Test
    fun unknownKeyIsSplit() {
        listOf(null, "", "x").forEach { assertEquals(DriveLayout.SPLIT, DriveLayout.of(it)) }
        assertEquals(DriveLayout.NAVI, DriveLayout.of("navi"))
        assertEquals(DriveLayout.SPLIT, DriveLayout.of("split"))
        assertEquals(DriveLayout.SPLIT_BYWAY, DriveLayout.of("split_byway"))
    }

    /** PM 11-11: 출발 때 세 가지 중에서 고른다. 칸 이름은 왼쪽부터다. */
    @Test
    fun threeLayoutsNameTheirPanes() {
        assertEquals("3분할 · 차량 정보 + 내비 + 에움길", DriveLayout.SPLIT.title)
        assertEquals("3분할 · 차량 정보 + 에움길 + 내비", DriveLayout.SPLIT_BYWAY.title)
        assertEquals("2분할 · 차량 정보 + 내비", DriveLayout.NAVI.title)
        assertEquals(listOf("차량 정보", "내비", "에움길"), DriveLayout.SPLIT.panes)
        assertEquals(listOf("차량 정보", "에움길", "내비"), DriveLayout.SPLIT_BYWAY.panes)
        assertEquals(listOf("차량 정보", "내비"), DriveLayout.NAVI.panes)
    }

    /** 앱은 화면을 직접 나눌 수 없다. 3분할은 에움길이 1/3 칸일 때, 2분할은 넓은 칸일 때 바로 출발한다. */
    @Test
    fun layoutFitsItsWindow() {
        assertTrue(DriveLayout.SPLIT.fits(WindowClass.THIRD))
        assertTrue(DriveLayout.SPLIT_BYWAY.fits(WindowClass.THIRD))
        assertFalse(DriveLayout.SPLIT.fits(WindowClass.TWO))
        assertTrue(DriveLayout.NAVI.fits(WindowClass.TWO))
        assertTrue(DriveLayout.NAVI.fits(WindowClass.FULL))
        assertFalse(DriveLayout.NAVI.fits(WindowClass.THIRD))
    }

    @Test
    fun dragHintSaysWhichWayToDrag() {
        assertEquals("오른쪽 끝 ⋮을 왼쪽으로 끌어 셋으로 나누면 출발합니다", dragHint(DriveLayout.SPLIT, WindowClass.TWO))
        assertEquals("⋮을 오른쪽 끝까지 끌어 2분할로 돌아가면 출발합니다", dragHint(DriveLayout.NAVI, WindowClass.THIRD))
        assertNull(dragHint(DriveLayout.SPLIT_BYWAY, WindowClass.THIRD))
        assertNull(dragHint(DriveLayout.NAVI, WindowClass.TWO))
    }

    private val request = StartRequest("c1", 3, stop = 2)

    /** 고르기 전에는 1/3 칸이어도 출발하지 않는다. */
    @Test
    fun nothingStartsBeforeChoosing() {
        assertNull(takeGuide(Nav(splitGuide = request), WindowClass.THIRD, parked = true, stop = request.stop).second)
    }

    /** 3분할을 고르고 넓은 창이면 기다리다가, 사용자가 나눠 1/3 칸이 되면 그 배치로 출발한다. */
    @Test
    fun splitWaitsThenStartsInThird() {
        val chosen = Nav(splitGuide = request, startLayout = DriveLayout.SPLIT_BYWAY)
        val (waiting, none) = takeGuide(chosen, WindowClass.TWO, parked = true, stop = request.stop)
        assertNull(none)
        assertEquals(chosen, waiting)
        val (done, ready) = takeGuide(chosen, WindowClass.THIRD, parked = true, stop = request.stop)
        assertEquals(ReadyStart(request, DriveLayout.SPLIT_BYWAY), ready)
        assertNull(done.splitGuide)
        assertNull(done.startLayout)
    }

    /** 2분할을 고르고 1/3 칸이면 기다리다가, 넓어지면 출발한다. */
    @Test
    fun naviWaitsThenStartsWhenWide() {
        val chosen = Nav(splitGuide = request, startLayout = DriveLayout.NAVI)
        assertNull(takeGuide(chosen, WindowClass.THIRD, parked = true, stop = request.stop).second)
        assertEquals(ReadyStart(request, DriveLayout.NAVI), takeGuide(chosen, WindowClass.TWO, parked = true, stop = request.stop).second)
    }

    /** 기다리는 중 기어를 D로 바꾼 뒤 나눠도 주행 중에는 출발하지 않는다. */
    @Test
    fun guideWaitsWhileMoving() {
        val chosen = Nav(splitGuide = request, startLayout = DriveLayout.SPLIT)
        assertNull(takeGuide(chosen, WindowClass.THIRD, parked = false, stop = request.stop).second)
    }

    /** 나눈 뒤 폭 변화가 두 번 들어와도 한 번만 출발한다. */
    @Test
    fun guideIsTakenOnce() {
        val (nav, _) = takeGuide(Nav(splitGuide = request, startLayout = DriveLayout.SPLIT), WindowClass.THIRD, parked = true, stop = request.stop)
        assertNull(takeGuide(nav, WindowClass.THIRD, parked = true, stop = request.stop).second)
    }

    /** 안내를 띄운 채 에움길이 가려져 있다가 운전하고 다시 정차하면, 지난 정차의 안내로 출발하지 않는다. */
    @Test
    fun guideDroppedAfterDriving() {
        val (nav, got) = takeGuide(Nav(splitGuide = request, startLayout = DriveLayout.SPLIT), WindowClass.THIRD, parked = true, stop = request.stop + 1)
        assertNull(got)
        assertNull(nav.splitGuide)
        assertNull(nav.startLayout)
    }
}
