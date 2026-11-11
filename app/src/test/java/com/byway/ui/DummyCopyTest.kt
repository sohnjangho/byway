package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DummyCopyTest {
    private val path = (0..3).map { GeoPoint(37.0, 127.0 + it * 0.1) }
    private val dummy = Course(
        "dummy-1-01", "더미 길", path,
        listOf(Section("가", 0, 1), Section("나", 2, 2), Section("다", 3, 3)), dummy = true, declaredMeters = 60_000.0,
    )
    private val real = dummy.copy(id = "real", dummy = false, declaredMeters = null)

    @Test
    fun measuredShowsDashForDummy() {
        assertEquals("—", measured(dummy, "12"))
        assertEquals("12", measured(real, "12"))
    }

    @Test
    fun dummyMarkGoesBeforeTheName() {
        assertEquals("더미 · A", withDummyMark(dummy, "A"))
        assertEquals("A", withDummyMark(real, "A"))
    }

    @Test
    fun dummyPlacesAreTheRemainingPoints() {
        val places = dummyPlaces(dummy, listOf(10_000.0, 20_000.0, 30_000.0))
        assertEquals(listOf(10.0, 30.0, 60.0), places.map { it.km })
        assertEquals(listOf("경유", "경유", "도착"), places.map { it.label })
        assertEquals(listOf("가", "나", "다"), places.map { it.name })
        assertEquals(listOf(1, 2, 3), places.map { it.no })
    }

    /** PM 11-11: 지도는 없더라도 코스 경로는 더미로 그린다. 선 옆에 예시 경로임을 밝힌다. */
    @Test
    fun copyIsFixed() {
        assertEquals("더미 경로", DUMMY_ROUTE)
        assertEquals("더미 데이터입니다 · 경로는 예시입니다", ATLAS_DUMMY_NOTE)
    }

    /** 주행 화면의 위치 점은 더미 경로 위에서 달린 거리(표기 거리 기준)만큼 나아간 자리다. */
    @Test
    fun dummyDotFollowsDrivenDistance() {
        val scale = 2.0
        assertEquals(dummy.path.first(), dummyDot(dummy, 0.0, scale))
        val line = dummy.path.zipWithNext { a, b -> a.distanceTo(b) }.sum()
        val end = dummyDot(dummy, line * scale / 1_000, scale)!!
        assertEquals(dummy.path.last().lon, end.lon, 1e-6)
        val half = dummyDot(dummy, line * scale / 2_000, scale)!!
        assertEquals(127.15, half.lon, 0.001)
    }
}
