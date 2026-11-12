package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import com.byway.service.DriveState
import com.byway.service.Recording
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

    /** 코스를 고르면 홈에 올라갈 뿐 화면 종류는 바뀌지 않는다. */
    @Test
    fun parkedWithChosenCourseStaysOnTab() {
        assertEquals(Screen.Browse(Tab.HOME), screenFor(parked, Nav(tab = Tab.HOME, courseId = "c1")))
    }

    /** 함께달리기는 내 코스 다음이다(함께달리기 설계 6-1). */
    @Test
    fun menuHasFiveEntries() {
        assertEquals(listOf("홈", "더 많은 코스", "랭킹", "내 코스", "함께달리기"), Tab.entries.map { it.title })
    }

    /** 리더가 고른 코스는 정차했을 때만 묻는다. 달리던 코스 이어가기가 먼저이고, 코스가 달리는 동안에는 묻지 않는다. */
    @Test
    fun aPartyQuestionComesAfterTheResumeOfferWhenParked() {
        assertEquals(Screen.PartyAsk, screenFor(parked, Nav(tab = Tab.TOGETHER), asking = true))
        assertEquals(Screen.PartyAsk, screenFor(parked, Nav(settings = true), asking = true))
        assertEquals(Screen.Resume, screenFor(parked, Nav(), resumable = true, asking = true))
        assertEquals(Screen.Waiting, screenFor(moving, Nav(), asking = true))
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true, course = course), Nav(), asking = true))
    }

    /** 달리다 만 코스가 있으면 정차 화면보다 먼저 이어갈지 묻는다. 달리는 중에는 묻지 않는다. */
    @Test
    fun aSavedRunIsOfferedBeforeTheMenusButOnlyWhenParked() {
        assertEquals(Screen.Resume, screenFor(parked, Nav(tab = Tab.RANKING, settings = true), resumable = true))
        assertEquals(Screen.Waiting, screenFor(moving, Nav(), resumable = true))
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true, course = course), Nav(), resumable = true))
    }

    @Test
    fun aFinishedCourseIsShownBeforeTheOffer() {
        assertEquals(Screen.Summary(course), screenFor(parked.copy(finished = course), Nav(), resumable = true))
    }

    @Test
    fun parkedWithAboutOpenIsAbout() {
        assertEquals(Screen.About, screenFor(parked, Nav(about = true)))
    }

    @Test
    fun parkedWithSettingsOpenIsSettings() {
        assertEquals(Screen.Settings, screenFor(parked, Nav(settings = true)))
    }

    /** 앱 정보는 설정에서 연다. 닫으면 설정으로 돌아온다. */
    @Test
    fun aboutOpensOverSettings() {
        assertEquals(Screen.About, screenFor(parked, Nav(settings = true, about = true)))
    }

    @Test
    fun movingHidesSettings() {
        Tab.entries.forEach { assertEquals(Screen.Waiting, screenFor(moving, Nav(tab = it, settings = true))) }
    }

    @Test
    fun runningCourseBeatsSettings() {
        assertEquals(Screen.Drive, screenFor(parked.copy(running = true), Nav(settings = true)))
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
    private val track = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.0, 127.01))

    @Test
    fun recordingShowsWhileDriving() {
        assertEquals(Screen.Recording, screenFor(moving.copy(recording = Recording(120.0, 8)), Nav()))
    }

    @Test
    fun recordingShowsWhileParked() {
        assertEquals(Screen.Recording, screenFor(parked.copy(recording = Recording(120.0, 8)), Nav(settings = true)))
    }

    @Test
    fun savingOnlyWhenParked() {
        assertEquals(Screen.SaveRecord, screenFor(parked.copy(recorded = track), Nav()))
        assertEquals(Screen.Waiting, screenFor(moving.copy(recorded = track), Nav()))
    }

    /** 기록 중 앱이 죽으면 새 상태에는 기록이 없다. 기록 화면에 갇히지 않는다. */
    @Test
    fun recordingNeedsLiveState() {
        assertEquals(Screen.Waiting, screenFor(DriveState(), Nav()))
        assertEquals(Screen.Browse(Tab.MINE), screenFor(parked, Nav(tab = Tab.MINE)))
    }

    @Test
    fun courseRunBeatsRecording() {
        assertEquals(Screen.Drive, screenFor(moving.copy(running = true, recording = Recording(0.0, 0)), Nav()))
    }

    @Test
    fun recordConsentOnlyWhenParked() {
        assertEquals(Screen.RecordConsent, screenFor(parked, Nav(tab = Tab.MINE, recordConsent = true)))
        assertEquals(Screen.Waiting, screenFor(moving, Nav(tab = Tab.MINE, recordConsent = true)))
    }

    private val request = StartRequest("c1", 0)

    /** 3분할 설정에서 화면을 나누라는 안내. 정차 중에만 보인다. */
    @Test
    fun splitGuideShowsWhenParked() {
        assertEquals(Screen.SplitGuide, screenFor(parked, Nav(splitGuide = request)))
    }

    /** 이어 달리기 버튼도 안내를 띄운다. 안내가 이어 달리기 물음보다 앞서야 보인다. */
    @Test
    fun splitGuideComesBeforeResume() {
        assertEquals(Screen.SplitGuide, screenFor(parked, Nav(splitGuide = request), resumable = true))
    }

    @Test
    fun splitGuideHiddenWhileMoving() {
        assertEquals(Screen.Waiting, screenFor(moving, Nav(splitGuide = request)))
    }
}
