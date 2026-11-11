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

    @Test
    fun copyIsFixed() {
        assertEquals("지도 없음 · 더미 데이터입니다", NO_MAP)
        assertEquals("더미 데이터입니다 · 경로선이 없습니다", ATLAS_DUMMY_NOTE)
    }
}
