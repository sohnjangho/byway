package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import com.byway.service.DriveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenForTest {
    private val course = Course(
        id = "c1",
        name = "코스",
        path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.1, 127.1)),
        sections = listOf(Section("구간", 0, 1)),
    )
    private val parked = DriveState(parked = true)
    private val moving = DriveState(parked = false)

    @Test
    fun parkedWithNothingChosenIsHome() {
        assertEquals(Screen.Home, screenFor(parked, null, false))
    }

    @Test
    fun parkedWithChosenCourseIsPreview() {
        assertEquals(Screen.Preview("c1"), screenFor(parked, "c1", false))
    }

    @Test
    fun parkedWithAboutOpenIsAbout() {
        assertEquals(Screen.About, screenFor(parked, null, true))
    }

    @Test
    fun runningCourseIsDriveWhileMoving() {
        assertEquals(Screen.Drive, screenFor(moving.copy(running = true), null, false))
    }

    @Test
    fun runningCourseStaysDriveAtAStop() {
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true), null, false))
    }

    @Test
    fun runningCourseBeatsChosenPreviewAndAbout() {
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true), "c1", true))
    }

    /** 완주 뒤 서비스가 내려가기 전까지는 running 과 finished 가 함께 참이다. */
    @Test
    fun finishedCourseStaysDriveUntilServiceStops() {
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true, finished = course), null, false))
    }

    @Test
    fun movingWithoutCourseWaits() {
        assertEquals(Screen.Waiting, screenFor(moving, null, false))
    }

    @Test
    fun movingHidesChosenPreview() {
        assertEquals(Screen.Waiting, screenFor(moving, "c1", false))
    }

    @Test
    fun movingHidesAbout() {
        assertEquals(Screen.Waiting, screenFor(moving, null, true))
    }

    @Test
    fun parkedAfterFinishIsSummary() {
        assertEquals(Screen.Summary(course), screenFor(parked.copy(finished = course), null, false))
    }

    @Test
    fun summaryBeatsLeftoverChoices() {
        assertEquals(Screen.Summary(course), screenFor(parked.copy(finished = course), "c1", true))
    }

    /** 요약에는 터치 버튼이 있으므로 P 단이 확인될 때까지 보여 주지 않는다. */
    @Test
    fun movingAfterFinishWaitsForParkBeforeSummary() {
        assertEquals(Screen.Waiting, screenFor(moving.copy(finished = course), null, false))
    }

    @Test
    fun onlyDriveOrWaitingEverShowWhileMoving() {
        val shown = buildSet {
            for (running in listOf(false, true))
                for (finished in listOf(null, course))
                    for (selected in listOf(null, "c1"))
                        for (about in listOf(false, true))
                            add(screenFor(moving.copy(running = running, finished = finished), selected, about))
        }
        assertTrue(shown.toString(), shown == setOf(Screen.Drive, Screen.Waiting))
    }
}
