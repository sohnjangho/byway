package com.byway.ui

import com.byway.core.course.funScore
import com.byway.core.course.naturePercent
import com.byway.core.course.Course
import com.byway.core.course.CourseFacts
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val facts = CourseFacts(
        meters = 50_340.0,
        directMeters = 24_000.0,
        eumRatio = 2.097,
        curves = 48,
        climbMeters = 713,
        topMeters = 469,
        moodShare = emptyMap(),
    )
    private val climbing = course(straight).copy(elevation = listOf(10.0, 20.0))

    @Test
    fun kilometersKeepOneDecimal() {
        assertEquals("50.3km", kmText(facts.meters))
    }

    @Test
    fun topIsBlankWithoutElevation() {
        assertEquals("-", topText(course(straight), facts))
    }

    @Test
    fun rankValueUsesTheChosenMeasure() {
        val ranked = Ranked(climbing, facts)
        assertEquals("48개", rankValue(RankBy.CURVES, ranked))
        assertEquals("${funScore(facts, hasElevation = true)}점", rankValue(RankBy.FUN, ranked))
        assertEquals("${naturePercent(facts.moodShare)}%", rankValue(RankBy.SCENERY, ranked))
        assertEquals("50.3km", rankValue(RankBy.LENGTH, ranked))
    }

    @Test
    fun theUnitStartsAfterTheNumber() {
        assertEquals(4, unitStart("50.3km"))
        assertEquals(3, unitStart("2.1배"))
        assertEquals(3, unitStart("713m"))
    }

    @Test
    fun aBareValueHasNoUnit() {
        assertEquals(2, unitStart("48"))
        assertEquals(1, unitStart("-"))
    }

    @Test
    fun moodsReadAsRoadKinds() {
        assertEquals(listOf("숲길", "도심", "강변"), listOf("forest", "city", "river").map(::moodWord))
    }

    @Test
    fun rankMeasuresReadAsTheirNames() {
        assertEquals(listOf("커브", "운전 즐거움", "경치", "거리"), RankBy.entries.map(::rankTitle))
    }

    @Test
    fun everyRankNoteSaysItIsNotPopularity() {
        RankBy.entries.forEach { assertTrue(rankNote(it), rankNote(it).startsWith("인기 순위가 아니라")) }
    }

    @Test
    fun timeWordFollowsTheSameHoursAsTheSuggestion() {
        val words = mapOf(
            4 to "오늘 밤", 5 to "오늘 아침", 8 to "오늘 아침", 9 to "오늘 낮",
            15 to "오늘 낮", 16 to "오늘 저녁", 19 to "오늘 저녁", 20 to "오늘 밤",
        )
        words.forEach { (hour, word) -> assertEquals("$word 달리기 좋은 길", suggestionLine(hour)) }
    }

    @Test
    fun naviPlanUsesDefaultToleranceWhenCourseHasNone() {
        assertEquals("코스 좌표 5개 → 내비 지점 1개 → 1번에 나눠 안내", naviPlanText(course(zigzag)))
    }

    @Test
    fun naviPlanGroupsThousandsOfCoursePoints() {
        val long = (0 until 1_540).map { GeoPoint(37.0 + it * 0.00001, 127.0) }
        assertTrue(naviPlanText(course(long)), naviPlanText(course(long)).startsWith("코스 좌표 1,540개"))
    }

    @Test
    fun naviPlanUsesCourseTolerance() {
        assertEquals("코스 좌표 5개 → 내비 지점 4개 → 2번에 나눠 안내", naviPlanText(course(zigzag, tolerance = 100.0)))
    }
}
