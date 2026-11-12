package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseTags
import com.byway.core.course.RankBy
import com.byway.core.course.Section
import com.byway.core.course.characterOf
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseBookTest {
    private val shortPath = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.045, 127.0))
    private val longPath = shortPath + GeoPoint(37.09, 127.0)

    private fun course(id: String, path: List<GeoPoint> = shortPath, themes: List<String> = emptyList()) = Course(
        id = id,
        name = id,
        path = path,
        sections = listOf(Section(id, 0, path.lastIndex)),
        tags = CourseTags(themes = themes),
    )

    private val short = course("short", themes = listOf("river", "desert"))
    private val long = course("long", longPath, themes = listOf("river"))
    private val book = CourseBook(listOf(short, long))

    @Test
    fun aCourseIsMeasuredOnce() {
        assertSame(book.facts(long), book.facts(long))
    }

    @Test
    fun aRankingIsSortedOnce() {
        assertSame(book.ranked(RankBy.LENGTH), book.ranked(RankBy.LENGTH))
    }

    @Test
    fun rankingUsesTheChosenMeasure() {
        assertEquals(listOf("long", "short"), book.ranked(RankBy.LENGTH).map { it.course.id })
    }

    @Test
    fun characterLineComesFromTheWholeCatalog() {
        assertEquals("가장 긴 길 10.0km", book.character(long))
    }

    /** 길이와 오르막이 코스마다 다르고 일부는 같다. 동점과 1·2위 경계를 함께 본다. */
    private val many = (1..12).map { i ->
        val steps = 2 + i % 5
        val path = (0..steps).map { GeoPoint(37.0 + it * 0.01 * (1 + i % 3), 127.0 + (it % 2) * 0.01 * (i % 4)) }
        course("c$i", path).copy(
            elevation = listOf(10.0, 10.0 + (i % 4) * 50),
            sections = listOf(Section("c$i", 0, path.lastIndex, if (i % 2 == 0) "forest" else "river")),
        )
    }

    /** 코스 전부의 특색을 구해도 코스 수의 제곱만큼 재지 않는다. 60개에서 화면이 2초 멈췄다. */
    @Test
    fun characterComparesAgainstOnlyTheLeadersOfEachMeasure() {
        assertTrue(CourseBook(many).rivals.size <= RankBy.entries.size * 2)
    }

    @Test
    fun comparingWithLeadersGivesTheSameLineAsTheWholeCatalog() {
        val book = CourseBook(many)
        many.forEach { assertEquals(it.id, characterOf(it, many), book.character(it)) }
    }

    /** 같은 수치의 코스가 둘이면 어느 쪽도 혼자 앞서지 않는다. 상대에서 빠뜨리면 "가장 긴 길"이 잘못 붙는다. */
    @Test
    fun aTieAtTheTopLeadsNeither() {
        fun twin(id: String) = course(id, longPath).copy(sections = listOf(Section(id, 0, longPath.lastIndex, "river")))
        val first = twin("first")
        val book = CourseBook(listOf(first, twin("second"), short))
        assertEquals("물가가 100%인 길", book.character(first))
    }

    @Test
    fun aThemeWithoutAWordIsLeftOutOfTheShelves() {
        assertEquals(listOf("river"), book.themes.map { it.key })
    }

    @Test
    fun aRecordedCourseThatIsGoneIsLeftOut() {
        assertEquals(listOf(long), book.known(listOf("gone", "long")))
    }

    @Test
    fun sectionLengthsAddUpToTheCourse() {
        val path = longPath
        val split = Course("c", "c", path, listOf(Section("a", 0, 0), Section("b", 1, 2)))
        val lengths = CourseBook(listOf(split)).sectionMeters(split)
        assertEquals(10_000.0, lengths.sum(), 50.0)
        assertEquals(5_000.0, lengths[0], 25.0)
    }

    @Test
    fun myCoursesPutRecentFirstWithoutRepeats() {
        val path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.1, 127.1))
        val made = listOf("a", "b", "c").map { Course(it, it, path, listOf(Section("구간", 0, 1))) }
        val book = CourseBook(made)
        assertEquals(listOf("b", "a", "c"), book.mine(setOf("c", "a", "없어진 코스"), listOf("b", "a")).map { it.id })
    }
}
