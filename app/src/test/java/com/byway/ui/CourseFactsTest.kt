package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseFactsTest {
    private fun course(path: List<GeoPoint>, sections: Int = 1, tolerance: Double? = null) = Course(
        id = "c1",
        name = "코스",
        path = path,
        sections = (0 until sections).map { i ->
            val size = path.size / sections
            Section("구간$i", i * size, if (i == sections - 1) path.lastIndex else (i + 1) * size - 1)
        },
        toleranceMeters = tolerance,
    )

    /** 위도 0.09도는 약 10km 다. */
    private val straight = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.045, 127.0), GeoPoint(37.09, 127.0))

    /** 꺾임이 약 1.1km 라 기본 허용 오차 1,200m 안에서는 사라지고 100m 에서는 모두 남는다. */
    private val zigzag = listOf(
        GeoPoint(37.00, 127.00),
        GeoPoint(37.01, 127.01),
        GeoPoint(37.00, 127.02),
        GeoPoint(37.01, 127.03),
        GeoPoint(37.00, 127.04),
    )

    @Test
    fun distanceAddsEveryStepInKilometers() {
        assertEquals("10.0km", distanceText(course(straight)))
    }

    @Test
    fun statsShowDistanceAndSectionCount() {
        assertEquals("10.0km · 구간 2개", statsText(course(straight, sections = 2)))
    }

    @Test
    fun sectionsReadInDrivingOrder() {
        assertEquals("구간0 → 구간1", sectionsText(course(straight, sections = 2)))
    }

    @Test
    fun naviPlanUsesDefaultToleranceWhenCourseHasNone() {
        assertEquals("코스 좌표 5개 → 내비 지점 1개 → 1번에 나눠 안내", naviPlanText(course(zigzag)))
    }

    @Test
    fun naviPlanUsesCourseTolerance() {
        assertEquals("코스 좌표 5개 → 내비 지점 4개 → 2번에 나눠 안내", naviPlanText(course(zigzag, tolerance = 100.0)))
    }
}
