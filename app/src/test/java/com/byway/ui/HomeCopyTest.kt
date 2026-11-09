package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseTags
import com.byway.core.course.SceneMood
import com.byway.core.course.ScenePlace
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCopyTest {
    private fun place(kind: String, note: String = "", km: Double = 1.5, no: Int = 0) =
        ScenePlace(no, "이름", kind, "경치", note, km, 0f, 0f)

    @Test
    fun everyMoodHasTheMockupKick() {
        assertEquals("노을이 지는 시간에 어울리는 길", kickFor(SceneMood.SUNSET))
        assertEquals("오늘 달리기 좋은 길", kickFor(SceneMood.PLAIN))
        assertEquals("비 오는 날 호숫가로 가는 길", kickFor(SceneMood.RAIN))
        SceneMood.entries.forEach { assertTrue(it.name, kickFor(it).isNotBlank()) }
    }

    @Test
    fun atMostTwoOfEachKindAreShownAndRenumbered() {
        val places = List(3) { place("view") } + List(3) { place("sight") } + List(3) { place("food") }
        val shown = shownPlaces(places)
        assertEquals(6, shown.size)
        assertEquals((1..6).toList(), shown.map { it.no })
    }

    @Test
    fun photoSpotsCountAsViews() {
        assertEquals(2, shownPlaces(listOf(place("view"), place("photo"), place("view"))).size)
    }

    @Test
    fun placeLineSkipsEmptyNote() {
        assertEquals("경치 · 1.5km 지점", placeLine(place("view")))
        assertEquals("경치 · 4·9일 오일장 · 12km 지점", placeLine(place("view", "4·9일 오일장", 12.0)))
    }

    @Test
    fun sceneryNeedsBothCreditAndFile() {
        assertFalse(canSwipeToScenery("", true))
        assertFalse(canSwipeToScenery("사진: x", false))
        assertTrue(canSwipeToScenery("사진: x", true))
    }

    @Test
    fun suggestionIsCachedPerMoodAndHour() {
        val path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.1, 127.1))
        val course = Course("c", "코스", path, listOf(Section("구간", 0, 1)), tags = CourseTags(times = listOf("sunset")))
        val book = CourseBook(listOf(course))
        assertSame(book.suggested(SceneMood.SUNSET, 18), book.suggested(SceneMood.SUNSET, 18))
    }

    @Test
    fun stripStaysPutWhenTheCardIsAlreadyVisible() {
        assertEquals(300, stripScrollFor(previous = 300, index = 2, step = 296, cardWidth = 284, viewWidth = 1500))
    }

    @Test
    fun stripMovesOnlyToBringAHiddenCardIn() {
        assertEquals(2960, stripScrollFor(previous = 0, index = 10, step = 296, cardWidth = 284, viewWidth = 1500))
    }

    @Test
    fun stripStartsAtTheChosenCard() {
        assertEquals(592, stripScrollFor(previous = null, index = 2, step = 296, cardWidth = 284, viewWidth = 1500))
    }

    /** 넓은 창의 홈은 운전석 쪽 버튼 줄에 풍경 버튼을 두고, 그 밖에는 지도 위 단추를 쓴다. */
    @Test
    fun theSceneryControlSitsOnTheDriversSide() {
        assertEquals(SceneryControl.BUTTON, sceneryControl(WindowClass.TWO, home = true))
        assertEquals(SceneryControl.BUTTON, sceneryControl(WindowClass.FULL, home = true))
        assertEquals(SceneryControl.CHIP, sceneryControl(WindowClass.THIRD, home = true))
        assertEquals(SceneryControl.CHIP, sceneryControl(WindowClass.TWO, home = false))
    }

    @Test
    fun paceIsWrittenInHoursAndMinutes() {
        assertEquals("35분", paceText(35))
        assertEquals("1시간 10분", paceText(70))
        assertEquals("1시간", paceText(60))
        assertEquals("3시간 15분", paceText(195))
    }

    @Test
    fun paceCellsNameTheThreeWaysToDrive() {
        val cells = paceCells(com.byway.core.course.Pace(70, 90, 195))
        assertEquals(listOf("가볍게", "쉬어 가며", "머물며"), cells.map { it.name })
        assertEquals(listOf("1시간 10분", "1시간 30분", "3시간 15분"), cells.map { it.time })
        assertEquals(listOf("쉬지 않고", "경치 좋은 곳 두 번", "명소와 식사까지"), cells.map { it.note })
    }
}
