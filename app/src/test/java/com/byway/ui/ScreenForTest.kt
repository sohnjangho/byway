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
    fun parkedWithNothingChosenIsHomeTab() {
        assertEquals(Screen.Browse(Tab.HOME), screenFor(parked, Nav()))
    }

    @Test
    fun parkedShowsTheChosenTab() {
        Tab.entries.forEach { assertEquals(Screen.Browse(it), screenFor(parked, Nav(tab = it))) }
    }

    @Test
    fun parkedWithChosenCourseIsPreview() {
        assertEquals(Screen.Preview("c1"), screenFor(parked, Nav(courseId = "c1")))
    }

    @Test
    fun chosenCourseBeatsTheTabUnderIt() {
        assertEquals(Screen.Preview("c1"), screenFor(parked, Nav(tab = Tab.RANKING, courseId = "c1")))
    }

    @Test
    fun parkedWithAboutOpenIsAbout() {
        assertEquals(Screen.About, screenFor(parked, Nav(about = true)))
    }

    @Test
    fun runningCourseIsDriveWhileMoving() {
        assertEquals(Screen.Drive, screenFor(moving.copy(running = true), Nav()))
    }

    @Test
    fun runningCourseStaysDriveAtAStop() {
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true), Nav()))
    }

    @Test
    fun runningCourseBeatsChosenPreviewAndAbout() {
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true), Nav(courseId = "c1", about = true)))
    }

    /** 완주 뒤 서비스가 내려가기 전까지는 running 과 finished 가 함께 참이다. */
    @Test
    fun finishedCourseStaysDriveUntilServiceStops() {
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true, finished = course), Nav()))
    }

    @Test
    fun movingWithoutCourseWaits() {
        assertEquals(Screen.Waiting, screenFor(moving, Nav()))
    }

    @Test
    fun movingHidesEveryTab() {
        Tab.entries.forEach { assertEquals(Screen.Waiting, screenFor(moving, Nav(tab = it))) }
    }

    @Test
    fun movingHidesChosenPreview() {
        assertEquals(Screen.Waiting, screenFor(moving, Nav(courseId = "c1")))
    }

    @Test
    fun movingHidesAbout() {
        assertEquals(Screen.Waiting, screenFor(moving, Nav(about = true)))
    }

    @Test
    fun parkedAfterFinishIsSummary() {
        assertEquals(Screen.Summary(course), screenFor(parked.copy(finished = course), Nav()))
    }

    @Test
    fun summaryBeatsLeftoverChoices() {
        assertEquals(Screen.Summary(course), screenFor(parked.copy(finished = course), Nav(courseId = "c1", about = true)))
    }

    /** 요약에는 터치 버튼이 있으므로 P 단이 확인될 때까지 보여 주지 않는다. */
    @Test
    fun movingAfterFinishWaitsForParkBeforeSummary() {
        assertEquals(Screen.Waiting, screenFor(moving.copy(finished = course), Nav()))
    }

    @Test
    fun onlyDriveOrWaitingEverShowWhileMoving() {
        val shown = buildSet {
            for (running in listOf(false, true))
                for (finished in listOf(null, course))
                    for (tab in Tab.entries)
                        for (selected in listOf(null, "c1"))
                            for (about in listOf(false, true))
                                add(
                                    screenFor(
                                        moving.copy(running = running, finished = finished),
                                        Nav(tab = tab, courseId = selected, about = about),
                                    ),
                                )
        }
        assertTrue(shown.toString(), shown == setOf(Screen.Drive, Screen.Waiting))
    }

    /** 달리는 코스가 없으면 주행 중에는 대기 화면 하나뿐이다. 어떤 탭이든 누를 곳이 남지 않는다. */
    @Test
    fun movingWithoutCourseIsOnlyEverWaiting() {
        val shown = buildSet {
            for (finished in listOf(null, course))
                for (tab in Tab.entries)
                    for (selected in listOf(null, "c1"))
                        for (about in listOf(false, true))
                            add(screenFor(moving.copy(finished = finished), Nav(tab = tab, courseId = selected, about = about)))
        }
        assertEquals(setOf<Screen>(Screen.Waiting), shown)
    }
}
