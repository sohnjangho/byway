package com.byway.core.course

import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseShelvesTest {
    private val long = (0..200).map { GeoPoint(37.0, 127.0 + it * 0.001) }
    private val short = (0..50).map { GeoPoint(37.0, 127.0 + it * 0.001) }
    private val zigzag = (0..60).map { GeoPoint(37.0 + (it / 10 % 2) * 0.01 + (it % 10) * 0.0 , 127.0 + it * 0.001) }

    private fun course(
        id: String,
        path: List<GeoPoint> = short,
        themes: List<String> = emptyList(),
        times: List<String> = emptyList(),
        region: String = "수도권",
        elevation: List<Double> = emptyList(),
    ) = Course(
        id, id, path, listOf(Section(id, 0, path.lastIndex, "river")),
        tags = CourseTags(themes = themes, times = times), region = region, elevation = elevation,
    )

    @Test
    fun rankingByLengthPutsTheLongestFirst() {
        val shelf = rank(listOf(course("short"), course("long", long)), RankBy.LENGTH)
        assertEquals(listOf("long", "short"), shelf.map { it.course.id })
    }

    @Test
    fun rankingByClimbPutsTheSteepestFirst() {
        val flat = course("flat", elevation = listOf(1.0, 2.0))
        val steep = course("steep", elevation = listOf(1.0, 500.0))
        assertEquals("steep", rank(listOf(flat, steep), RankBy.CLIMB).first().course.id)
    }

    @Test
    fun aThemeShelfHoldsOnlyThatTheme() {
        val shelf = withTheme(listOf(course("a", themes = listOf("river")), course("b", themes = listOf("lake"))), "river")
        assertEquals(listOf("a"), shelf.map { it.id })
    }

    @Test
    fun themesAreCountedAndOrderedByCount() {
        val courses = listOf(
            course("a", themes = listOf("river", "forest")),
            course("b", themes = listOf("forest")),
        )
        assertEquals(listOf(Shelf("forest", 2), Shelf("river", 1)), themes(courses))
    }

    @Test
    fun regionsKeepTheirFirstSeenOrderWhenCountsTie() {
        val courses = listOf(course("a", region = "제주"), course("b", region = "강원"))
        assertEquals(listOf("제주", "강원"), regions(courses).map { it.key })
    }

    @Test
    fun aCourseWithoutARegionIsLeftOutOfRegions() {
        assertTrue(regions(listOf(course("a", region = ""))).isEmpty())
    }

    @Test
    fun eveningSuggestsASunsetCourse() {
        val courses = listOf(course("day", times = listOf("day")), course("sunset", times = listOf("sunset")))
        assertEquals("sunset", suggestFor(courses, hour = 17).id)
    }

    @Test
    fun earlyMorningSuggestsASunriseCourse() {
        val courses = listOf(course("day", times = listOf("day")), course("dawn", times = listOf("sunrise")))
        assertEquals("dawn", suggestFor(courses, hour = 6).id)
    }

    @Test
    fun anyCourseIsSuggestedWhenNoneFitsTheHour() {
        assertEquals("a", suggestFor(listOf(course("a", times = listOf("day"))), hour = 23).id)
    }

    @Test
    fun theLongestCourseIsCalledTheLongest() {
        val all = listOf(course("short"), course("long", long))
        assertTrue(characterOf(all.last(), all).contains("가장 긴 길"))
    }

    @Test
    fun theSteepestCourseIsCalledTheHighest() {
        val hill = course("hill", elevation = listOf(10.0, 700.0))
        val all = listOf(course("long", long), hill)
        assertTrue(characterOf(hill, all).contains("가장 높이 오르는 길"))
    }

    @Test
    fun anOrdinaryCourseFallsBackToItsLargestSection() {
        val forest = Course(
            "f", "f", short, listOf(Section("숲", 0, short.lastIndex, "forest")),
        )
        val all = listOf(course("long", long, elevation = listOf(0.0, 900.0)), zig(), forest)
        assertEquals("숲이 100%인 길", characterOf(forest, all))
    }

    private fun zig(): Course {
        val path = (0..200).map { GeoPoint(37.0 + (it / 5 % 2) * 0.004, 127.0 + (it / 5) * 0.004) }
        return course("zig", path)
    }
}
