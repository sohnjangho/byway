package com.byway.core.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseFitTest {
    private val seoulLat = 37.57
    private val seoulLon = 126.98

    /** 한국천문연구원 서울 일출몰표와 맞춰 본다. 위치 하나의 값이라 몇 분 차이는 둔다. */
    @Test
    fun sunTimesMatchTheAlmanacForSeoul() {
        val june = sunTimes(172, seoulLat, seoulLon)
        assertEquals(5 * 60 + 11, june.riseMinute, 5)
        assertEquals(19 * 60 + 57, june.setMinute, 5)
        val december = sunTimes(356, seoulLat, seoulLon)
        assertEquals(7 * 60 + 43, december.riseMinute, 5)
        assertEquals(17 * 60 + 17, december.setMinute, 5)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) =
        assertTrue("$actual 은 $expected 에서 $delta 안이어야 합니다", kotlin.math.abs(expected - actual) <= delta)

    // 계절 적합도

    @Test
    fun aCourseIsAtItsBestAroundItsSeasonPeak() {
        assertEquals(100, seasonFit(listOf("autumn"), 298))
        assertEquals(100, seasonFit(listOf("autumn"), 313))
    }

    @Test
    fun theFitFallsAwayFromThePeakButNeverToNothing() {
        val fits = listOf(300, 340, 15, 60, 100, 118).map { requireNotNull(seasonFit(listOf("autumn"), it)) }
        assertEquals(fits.sortedDescending(), fits)
        assertEquals(20, seasonFit(listOf("autumn"), 115))
    }

    /** 1월의 겨울 코스는 해가 바뀌어도 가깝다. */
    @Test
    fun seasonsWrapAroundTheNewYear() {
        assertEquals(100, seasonFit(listOf("winter"), 360))
    }

    @Test
    fun theBestOfSeveralSeasonsCounts() {
        assertEquals(seasonFit(listOf("spring"), 100), seasonFit(listOf("autumn", "spring"), 100))
    }

    @Test
    fun theFitMovesInStepsOfFive() {
        (1..365).forEach { assertEquals(0, requireNotNull(seasonFit(listOf("spring"), it)) % 5) }
    }

    @Test
    fun aCourseWithoutASeasonHasNoFit() {
        assertNull(seasonFit(emptyList(), 100))
        assertNull(seasonFit(listOf("monsoon"), 100))
    }

    // 시간 적합도

    private val sun = SunTimes(riseMinute = 7 * 60, setMinute = 18 * 60)

    @Test
    fun aDayCourseFitsAtNoon() {
        assertEquals(100, timeFit(listOf("day"), 12 * 60, 90, sun))
    }

    @Test
    fun onlyTheDaylightPartOfADriveCounts() {
        // 일몰 30분 전까지가 낮이다. 17:00 출발 60분이면 30분만 낮이다.
        assertEquals(50, timeFit(listOf("day"), 17 * 60, 60, sun))
    }

    @Test
    fun aSunsetCourseFitsOnlyAroundSunset() {
        assertEquals(100, timeFit(listOf("sunset"), 17 * 60, 70, sun))
        assertEquals(0, timeFit(listOf("sunset"), 12 * 60, 70, sun))
    }

    @Test
    fun aSunriseCourseFitsOnlyAroundSunrise() {
        assertEquals(100, timeFit(listOf("sunrise"), 6 * 60 + 40, 60, sun))
        assertEquals(0, timeFit(listOf("sunrise"), 15 * 60, 60, sun))
    }

    /** 밤은 자정을 넘어 이어진다. */
    @Test
    fun aNightCourseFitsAcrossMidnight() {
        assertEquals(100, timeFit(listOf("night"), 23 * 60 + 30, 60, sun))
        assertEquals(0, timeFit(listOf("night"), 12 * 60, 60, sun))
    }

    @Test
    fun severalTimesAddUpWithoutCountingTwice() {
        // 낮은 17:30까지, 노을은 17:00부터다. 겹쳐도 100%를 넘지 않는다.
        assertEquals(100, timeFit(listOf("day", "sunset"), 16 * 60 + 30, 90, sun))
    }

    @Test
    fun aCourseWithoutATimeHasNoFit() {
        assertNull(timeFit(emptyList(), 720, 60, sun))
        assertNull(timeFit(listOf("day"), 720, 0, sun))
    }

    // 운전 즐거움

    private fun facts(km: Double, curves: Int, climb: Int, eum: Double, city: Int) =
        CourseFacts(km * 1000, km * 1000 / eum, eum, curves, climb, 0, mapOf("city" to city, "forest" to 100 - city))

    @Test
    fun curvyHillyRoadsAwayFromTownScoreHigher() {
        val twisty = funScore(facts(40.0, 60, 800, 2.5, 0), hasElevation = true)
        val plain = funScore(facts(40.0, 10, 80, 1.1, 80), hasElevation = true)
        assertTrue("$twisty > $plain", twisty > plain + 30)
    }

    @Test
    fun theScoreStaysBetweenZeroAndAHundred() {
        assertEquals(100, funScore(facts(10.0, 100, 5000, 9.0, 0), hasElevation = true))
        assertTrue(funScore(facts(10.0, 0, 0, 1.0, 100), hasElevation = true) in 0..5)
    }

    /** 고도 자료가 없으면 평지로 치지 않고 나머지 값으로 점수를 낸다. */
    @Test
    fun aCourseWithoutElevationIsNotTreatedAsFlat() {
        val known = funScore(facts(40.0, 60, 1500, 2.5, 0), hasElevation = true)
        val unknown = funScore(facts(40.0, 60, 0, 2.5, 0), hasElevation = false)
        assertEquals(known, unknown, 3)
    }

    @Test
    fun courseLengthAloneDoesNotRaiseTheScore() {
        assertEquals(funScore(facts(20.0, 20, 300, 2.0, 10), true), funScore(facts(40.0, 40, 600, 2.0, 10), true))
    }

    // 연료량

    @Test
    fun aPlausibleFuelEconomyIsKept() {
        assertEquals(EnergyRate(electric = false, kmPerUnit = 12.5), plausibleRate(electric = false, 12.5))
    }

    @Test
    fun aPlausibleEvEfficiencyIsKept() {
        assertEquals(EnergyRate(electric = true, kmPerUnit = 5.2), plausibleRate(electric = true, 5.2))
    }

    /** 단위가 다르거나 아직 값이 쌓이지 않은 차에서 엉뚱한 양을 보이느니 칸을 숨긴다. */
    @Test
    fun anImplausibleRateIsDropped() {
        listOf(0.0, -3.0, 2.0, 180.0).forEach { assertNull("$it", plausibleRate(electric = false, it)) }
        listOf(0.0, 1.0, 40.0, 180.0).forEach { assertNull("$it", plausibleRate(electric = true, it)) }
        assertNull(plausibleRate(electric = false, null))
        assertNull(plausibleRate(electric = true, Double.NaN))
    }

    @Test
    fun energyNeededIsDistanceOverRate() {
        assertEquals(5.0, energyNeeded(50_000.0, EnergyRate(false, 10.0)), 1e-9)
    }

    // 경치

    @Test
    fun onlyViewsAndPhotoSpotsCountAsViews() {
        val p = com.byway.core.geo.GeoPoint(37.0, 127.0)
        val spots = listOf("view", "photo", "food", "rest").map { Spot(it, it, p, 0) }
        assertEquals(2, viewCount(Course("c", "코스", listOf(p, p), listOf(Section("구", 0, 1)), spots = spots)))
    }

    @Test
    fun natureIsTheShareOutsideTown() {
        assertEquals(80, naturePercent(mapOf("city" to 20, "forest" to 30, "river" to 50)))
        assertEquals(0, naturePercent(mapOf("city" to 100)))
    }
}
