package com.byway.core.gleo

import com.byway.core.course.Course
import com.byway.core.course.CourseTags
import com.byway.core.course.NearRange
import com.byway.core.course.RankBy
import com.byway.core.course.Section
import com.byway.core.course.rank
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchTest {
    private val seoul = GeoPoint(37.5663, 126.9779)

    /** 서울에서 북쪽으로 [km] 떨어진 자리에서 출발하는 코스. [steps]만큼 길이가 늘어난다. */
    private fun course(
        id: String,
        km: Double = 0.0,
        name: String = id,
        region: String = "수도권",
        tags: CourseTags = CourseTags(),
        dummy: Boolean = false,
        steps: Int = 1,
    ): Course {
        val start = GeoPoint(seoul.lat + km / 111.2, seoul.lon)
        val path = (0..steps).map { GeoPoint(start.lat + 0.01 * it, start.lon) }
        return Course(id, name, path, listOf(Section("길", 0, steps)), tags = tags, region = region, dummy = dummy)
    }

    private fun find(courses: List<Course>, query: VoiceQuery, here: GeoPoint? = seoul, mine: Set<String> = emptySet(), words: (Course) -> List<String> = { emptyList() }) =
        findForVoice(courses, query, here, mine, { 20_000.0 }, words)

    @Test
    fun realBeforeDummyThenNearest() {
        val all = listOf(course("dummy1", 1.0, name = "바다 길", dummy = true), course("real40", 40.0, name = "바다 길 둘"), course("real10", 10.0, name = "바다 길 셋"))
        assertEquals(listOf("real10", "real40", "dummy1"), find(all, VoiceQuery(searchTerm = "바다")).top.map { it.course.id })
    }

    @Test
    fun topIsAtMostThree() {
        val hits = find((1..5).map { course("c$it", it.toDouble(), name = "노을 $it") }, VoiceQuery(searchTerm = "노을"))
        assertEquals(5, hits.total)
        assertEquals(3, hits.top.size)
    }

    @Test
    fun rankByDropsDummiesAndUsesRankOrder() {
        val real = listOf(course("short", steps = 1), course("long", steps = 5), course("mid", steps = 3))
        val all = real + course("dummy", dummy = true, steps = 9)
        val hits = find(all, VoiceQuery(rankBy = "length"))
        assertEquals(rank(real, RankBy.LENGTH).map { it.course.id }, hits.top.map { it.course.id })
        assertFalse(hits.top.any { it.course.dummy })
    }

    @Test
    fun nearbyRoundsUpToChip() {
        assertEquals(NearRange.KM25, nearRangeFor(10.0))
        assertEquals(NearRange.KM25, nearRangeFor(25.0))
        assertEquals(NearRange.KM50, nearRangeFor(30.0))
        assertEquals(NearRange.KM75, nearRangeFor(60.0))
        assertEquals(NearRange.KM75, nearRangeFor(200.0))
    }

    @Test
    fun nearbyWithoutPositionIsSkippedAndFlagged() {
        val all = listOf(course("far", 70.0), course("close", 10.0))
        val hits = find(all, VoiceQuery(nearbyKm = 25.0), here = null)
        assertTrue(hits.positionUnknown)
        assertEquals(2, hits.total)
        assertFalse(find(all, VoiceQuery(nearbyKm = 25.0)).positionUnknown)
        assertEquals(listOf("close"), find(all, VoiceQuery(nearbyKm = 25.0)).top.map { it.course.id })
    }

    @Test
    fun myCoursesOnlyKeepsMine() {
        val all = listOf(course("a"), course("b"), course("c"))
        assertEquals(listOf("b"), find(all, VoiceQuery(myCoursesOnly = true), mine = setOf("b")).top.map { it.course.id })
    }

    @Test
    fun moodEnumFilters() {
        val all = listOf(course("sunset", tags = CourseTags(times = listOf("sunset"))), course("plain"))
        assertEquals(listOf("sunset"), find(all, VoiceQuery(mood = "sunset")).top.map { it.course.id })
        assertEquals(2, find(all, VoiceQuery(mood = "unknown-mood")).total)
    }

    @Test
    fun exactNameIgnoresSpacesAndCase() {
        val all = listOf(course("p", name = "팔당 노을"), course("p2", name = "팔당 노을 긴 길"), course("m", name = "Morning River"))
        assertEquals(NameMatch.One(all[0]), matchCourse(all, "팔당노을") { emptyList() })
        assertEquals(NameMatch.One(all[0]), matchCourse(all, "팔당 노을") { emptyList() })
        assertEquals(NameMatch.One(all[2]), matchCourse(all, "morning river") { emptyList() })
    }

    @Test
    fun ambiguousNameAsks() {
        val all = (1..4).map { course("n$it", name = "노을 길 $it") }
        val got = matchCourse(all, "노을") { emptyList() }
        assertTrue(got is NameMatch.Many)
        assertEquals(3, (got as NameMatch.Many).candidates.size)
    }

    @Test
    fun unknownNameIsNone() {
        assertEquals(NameMatch.None, matchCourse(listOf(course("a", name = "팔당 노을")), "없는길") { emptyList() })
        assertEquals(NameMatch.None, matchCourse(listOf(course("a", name = "팔당 노을")), "  ") { emptyList() })
    }

    @Test
    fun wordsMatchRegionAndTags() {
        val sea = course("sea", name = "애월 길", region = "제주")
        val all = listOf(sea, course("hill", name = "한라 길", region = "제주"), course("east", name = "동해 길", region = "강원"))
        val got = matchCourse(all, "제주 바다") { if (it.id == "sea" || it.id == "east") listOf("바다") else emptyList() }
        assertEquals(NameMatch.One(sea), got)
    }

    /** "팔당 노을 코스로 가자"처럼 말하면 courseName 에 "코스"가 붙어 온다(검토 M7). */
    @Test
    fun courseWordIsIgnored() {
        val all = listOf(course("p", name = "팔당 노을"), course("m", name = "Morning River"))
        assertEquals(NameMatch.One(all[0]), matchCourse(all, "팔당 노을 코스") { emptyList() })
        assertEquals(NameMatch.One(all[0]), matchCourse(all, "팔당노을코스") { emptyList() })
        assertEquals(NameMatch.One(all[1]), matchCourse(all, "Morning River course") { emptyList() })
        assertEquals(1, find(all, VoiceQuery(searchTerm = "팔당 코스")).total)
    }
}
