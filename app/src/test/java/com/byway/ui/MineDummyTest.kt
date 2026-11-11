package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/** 내 코스 목록 줄에도 더미 표시가 붙는다(검토 I-4). */
class MineDummyTest {
    @Test
    fun mineListMarksDummies() {
        val path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.0, 127.1))
        val dummy = Course("dummy-1-01", "더미 길", path, listOf(Section("끝", 0, 1)), dummy = true, declaredMeters = 10_000.0)
        assertEquals("더미 · 더미 길", mineListName(dummy))
        assertEquals("진짜 길", mineListName(dummy.copy(name = "진짜 길", dummy = false)))
    }
}
