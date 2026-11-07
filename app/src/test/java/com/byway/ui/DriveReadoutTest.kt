package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveReadoutTest {
    /** 위도 0.09도는 약 10km 다. 구간 둘이 각각 10km 다. */
    private val course = Course(
        id = "c1",
        name = "코스",
        path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.09, 127.0), GeoPoint(37.18, 127.0)),
        sections = listOf(Section("숲길", 0, 0, "forest"), Section("강변", 1, 2, "river")),
    )
    private val meters = listOf(10_000.0, 10_000.0)

    private fun readout(line: String) = readoutOf(course, meters, line)

    @Test
    fun beforeTheFirstLineTheCourseStartsAtItsFirstSection() {
        val readout = readout("코스")
        assertEquals(0, readout.sectionIndex)
        assertEquals("20.0", readout.remainingKm)
        assertEquals(listOf(0f, 0f), readout.filled)
    }

    @Test
    fun theEngineLineNamesTheSection() {
        assertEquals(1, readout("강변 · 남은 거리 7.5km").sectionIndex)
    }

    @Test
    fun theEngineLineGivesTheRemainingDistanceAsWritten() {
        assertEquals("7.5", readout("강변 · 남은 거리 7.5km").remainingKm)
    }

    @Test
    fun aPassedSectionIsFullAndTheCurrentOneIsPartlyFilled() {
        assertEquals(listOf(1f, 0.25f), readout("강변 · 남은 거리 7.5km").filled)
    }

    @Test
    fun theFirstSectionFillsWhileTheNextStaysEmpty() {
        assertEquals(listOf(0.5f, 0f), readout("숲길 · 남은 거리 15.0km").filled)
    }

    @Test
    fun aSectionNameTheCourseDoesNotHaveFallsBackToTheFirstSection() {
        assertEquals(0, readout("없는 구간 · 남은 거리 7.5km").sectionIndex)
    }
}
