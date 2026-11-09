package com.byway.core.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceTest {
    private val paldang = paceOf(50_300.0, 48, listOf("view", "view", "view", "photo", "sight", "sight", "food", "food"))

    @Test
    fun aLongWindingCourseTakesAboutSeventyMinutesWithoutStops() {
        assertEquals(70, paldang.light)
    }

    @Test
    fun restingAddsTwoShortStopsAtViews() {
        assertEquals(90, paldang.rest)
    }

    /** 조금 천천히 달리고 경치 10분씩, 명소 20분씩, 식사는 한 번 40분. */
    @Test
    fun stayingVisitsEveryPlaceAndEatsOnce() {
        assertEquals(195, paldang.stay)
    }

    @Test
    fun aShortCourseIsFaster() {
        assertEquals(Pace(35, 55, 140), paceOf(26_500.0, 20, listOf("view", "view", "sight", "sight", "food", "food")))
    }

    @Test
    fun aCourseWithOneViewRestsOnce() {
        val pace = paceOf(26_500.0, 20, listOf("view"))
        assertEquals(pace.light + 10, pace.rest)
    }

    /** 주변 정보가 없는 새 코스도 세 값이 나오고 순서가 뒤집히지 않는다. */
    @Test
    fun aCourseWithoutPlacesStillHasThreeOrderedTimes() {
        val pace = paceOf(26_500.0, 20, emptyList())
        assertTrue(pace.light > 0)
        assertEquals(pace.light, pace.rest)
        assertTrue(pace.stay >= pace.rest)
    }

    @Test
    fun timesAreRoundedToFiveMinutes() {
        listOf(paldang.light, paldang.rest, paldang.stay).forEach { assertEquals(0, it % 5) }
    }

    /** 굽이가 아무리 많아도 속도는 바닥 아래로 내려가지 않는다. */
    @Test
    fun curvesSlowTheDriveOnlyDownToAFloor() {
        assertEquals(paceOf(10_000.0, 15, emptyList()), paceOf(10_000.0, 500, emptyList()))
        assertTrue(paceOf(10_000.0, 15, emptyList()).light > paceOf(10_000.0, 0, emptyList()).light)
    }

    @Test
    fun aCourseWithNoLengthDoesNotBreak() {
        assertEquals(Pace(5, 5, 5), paceOf(0.0, 0, emptyList()))
    }
}
