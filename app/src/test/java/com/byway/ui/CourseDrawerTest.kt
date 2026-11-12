package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseDrawerTest {
    /** 넓은 창은 운전석 쪽 글 칸 위로, 지도 위에 글 칸이 놓이는 1/3 창은 아래에서 나온다. */
    @Test
    fun theDrawerComesFromTheDriverSideOrFromBelow() {
        assertEquals(DrawerEdge.LEFT, drawerEdge(WindowClass.TWO))
        assertEquals(DrawerEdge.LEFT, drawerEdge(WindowClass.FULL))
        assertEquals(DrawerEdge.BOTTOM, drawerEdge(WindowClass.THIRD))
    }

    /** 지금 코스를 맨 위에 두어 목록을 넘기지 않아도 어디에 있는지 보인다. 나머지는 원래 차례다. */
    @Test
    fun theChosenCourseLeadsTheList() {
        assertEquals(listOf("c", "a", "b", "d"), drawerOrder(listOf("a", "b", "c", "d"), "c") { it })
        assertEquals(listOf("a", "b"), drawerOrder(listOf("a", "b"), "없는 코스") { it })
    }

    @Test
    fun aRowSaysWhereHowFarAndForWhom() {
        assertEquals("수도권 · 50.3km · 가을에 추천", courseLine("수도권", 50_300.0, "가을에 추천"))
        assertEquals("28.0km", courseLine("", 28_000.0, ""))
    }

    /** 닫혀 있을 때는 화면 밖이고, 열리면 제자리다. */
    @Test
    fun theDrawerSlidesInFromItsEdge() {
        assertEquals(-620f to 0f, drawerShift(DrawerEdge.LEFT, 0f, 620f, 900f))
        assertEquals(0f to 900f, drawerShift(DrawerEdge.BOTTOM, 0f, 620f, 900f))
        assertEquals(0f to 0f, drawerShift(DrawerEdge.LEFT, 1f, 620f, 900f))
        assertEquals(0f to 450f, drawerShift(DrawerEdge.BOTTOM, 0.5f, 620f, 900f))
    }
}
