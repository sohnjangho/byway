package com.byway.core.course

import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseFilterTest {
    private val seoul = GeoPoint(37.5663, 126.9779)

    /** 서울에서 북쪽으로 [km] 떨어진 자리에서 출발하는 코스. 위도 1도는 약 111.2km 다. */
    private fun course(
        id: String,
        km: Double = 0.0,
        name: String = id,
        region: String = "수도권",
        tags: CourseTags = CourseTags(),
        meters: Double = 20_000.0,
    ): Course {
        val start = GeoPoint(seoul.lat + km / 111.2, seoul.lon)
        return Course(id, name, listOf(start, GeoPoint(start.lat + 0.01, start.lon)), listOf(Section("길", 0, 1)),
            tags = tags, region = region, dummy = true, declaredMeters = meters)
    }

    private fun run(courses: List<Course>, filter: CourseFilter, here: GeoPoint? = seoul, words: (Course) -> List<String> = { emptyList() }) =
        filterCourses(courses, filter, here, { it.declaredMeters ?: 0.0 }, words)

    @Test
    fun bandEdges() {
        assertEquals(LengthBand.UP_TO_30, bandOf(30_000.0))
        assertEquals(LengthBand.UP_TO_60, bandOf(30_000.1))
        assertEquals(LengthBand.UP_TO_60, bandOf(60_000.0))
        assertEquals(LengthBand.UP_TO_100, bandOf(100_000.0))
        assertEquals(LengthBand.OVER_100, bandOf(100_000.1))
    }

    @Test
    fun nearKeepsWithinAndSortsByDistance() {
        val all = listOf(course("far", 70.0), course("mid", 40.0), course("close", 10.0))
        assertEquals(listOf("close"), run(all, CourseFilter(near = NearRange.KM25)).map { it.course.id })
        assertEquals(listOf("close", "mid"), run(all, CourseFilter(near = NearRange.KM50)).map { it.course.id })
        val found = run(all, CourseFilter(near = NearRange.KM75))
        assertEquals(listOf("close", "mid", "far"), found.map { it.course.id })
        assertEquals(10_000.0, found.first().awayMeters!!, 200.0)
    }

    @Test
    fun nearIgnoredWithoutPosition() {
        val all = listOf(course("far", 70.0), course("close", 10.0))
        assertEquals(listOf("far", "close"), run(all, CourseFilter(near = NearRange.KM25), here = null).map { it.course.id })
    }

    @Test
    fun moodMatchesEachKind() {
        val winter = course("winter", tags = CourseTags(seasons = listOf("winter")))
        val rain = course("rain", tags = CourseTags(weathers = listOf("rain")))
        val sunset = course("sunset", tags = CourseTags(times = listOf("sunset")))
        val family = course("family", tags = CourseTags(moods = listOf("family")))
        val all = listOf(winter, rain, sunset, family)
        assertEquals(listOf("winter"), run(all, CourseFilter(mood = "seasons:winter")).map { it.course.id })
        assertEquals(listOf("rain"), run(all, CourseFilter(mood = "weathers:rain")).map { it.course.id })
        assertEquals(listOf("sunset"), run(all, CourseFilter(mood = "times:sunset")).map { it.course.id })
        assertEquals(listOf("family"), run(all, CourseFilter(mood = "moods:family")).map { it.course.id })
    }

    @Test
    fun queryNeedsEveryWord() {
        val all = listOf(course("a", name = "가평 북한강 노을길"), course("b", name = "abc 길"))
        assertEquals(listOf("a"), run(all, CourseFilter(query = "가평 노을")).map { it.course.id })
        assertEquals(emptyList<String>(), run(all, CourseFilter(query = "가평 바다")).map { it.course.id })
        assertEquals(listOf("a", "b"), run(all, CourseFilter(query = "  ")).map { it.course.id })
        assertEquals(listOf("b"), run(all, CourseFilter(query = "ABC")).map { it.course.id })
        assertEquals(listOf("a", "b"), run(all, CourseFilter(query = "수도권")).map { it.course.id })
    }

    @Test
    fun queryMatchesWordsOf() {
        val all = listOf(course("a"), course("b"))
        val found = run(all, CourseFilter(query = "비 오는"), words = { if (it.id == "a") listOf("비 오는 날") else emptyList() })
        assertEquals(listOf("a"), found.map { it.course.id })
    }

    @Test
    fun filtersCombineWithAnd() {
        val rainy = CourseTags(weathers = listOf("rain"))
        val all = listOf(
            course("hit", region = "강원", tags = rainy, meters = 45_000.0),
            course("wrongRegion", region = "제주", tags = rainy, meters = 45_000.0),
            course("wrongLength", region = "강원", tags = rainy, meters = 25_000.0),
            course("wrongMood", region = "강원", meters = 45_000.0),
        )
        val filter = CourseFilter(region = "강원", length = LengthBand.UP_TO_60, mood = "weathers:rain")
        assertEquals(listOf("hit"), run(all, filter).map { it.course.id })
        assertTrue(CourseFilter().isEmpty())
        assertTrue(!filter.isEmpty())
    }

    @Test
    fun themeKeepsCurrentRule() {
        val all = listOf(course("sea", tags = CourseTags(themes = listOf("coast"))), course("hill"))
        assertEquals(listOf("sea"), run(all, CourseFilter(theme = "coast")).map { it.course.id })
    }

    @Test
    fun moodKeysAreTwelve() {
        assertEquals(12, MOOD_KEYS.size)
        assertEquals("moods:alone", MOOD_KEYS.first())
        assertTrue("weathers:rain" in MOOD_KEYS)
        assertNotNull(MOOD_KEYS.firstOrNull { it == "times:night" })
    }
}
