package com.byway.core.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 앱에 들어가는 더미 코스 자료(더 많은 코스 설계 4절)를 확인한다. */
class DummyAssetTest {
    private val dummies by lazy { parseDummyCourses(File("../app/src/main/assets/dummy/courses.json").readText()) }
    private val real by lazy { File("../app/src/main/assets/courses").listFiles().orEmpty().map { parseCourse(it.readText()) } }

    @Test
    fun hundredFiftyByRegion() {
        assertEquals(150, dummies.size)
        val expected = mapOf("수도권" to 30, "강원" to 25, "충청" to 20, "경상" to 25, "전라" to 20, "남해안" to 15, "제주" to 15)
        assertEquals(expected, dummies.groupingBy { it.region }.eachCount())
    }

    @Test
    fun idsUniqueAndPrefixed() {
        assertTrue(dummies.all { it.id.startsWith("dummy-") })
        assertEquals(dummies.size, dummies.map { it.id }.toSet().size)
        assertTrue(dummies.none { d -> real.any { it.id == d.id } })
        assertTrue(dummies.none { d -> real.any { it.name == d.name } })
        assertEquals(dummies.size, dummies.map { it.name }.toSet().size)
    }

    @Test
    fun pointsInKorea() {
        dummies.forEach { course ->
            assertTrue(course.id, course.sections.size >= 2)
            course.path.forEach { p -> assertTrue(course.id, p.lat in 33.0..38.7 && p.lon in 124.5..131.0) }
        }
    }

    @Test
    fun tagsKnown() {
        dummies.forEach { c ->
            assertTrue(c.id, c.tags.themes.isNotEmpty() && c.tags.themes.all { it in setOf("river", "forest", "mountain", "lake", "city", "coast") })
            assertTrue(c.id, c.tags.moods.all { it in setOf("alone", "date", "family") })
            assertTrue(c.id, c.tags.times.all { it in setOf("sunrise", "day", "sunset", "night") })
            assertTrue(c.id, c.tags.seasons.all { it in setOf("spring", "summer", "autumn", "winter") })
            assertTrue(c.id, c.tags.weathers.all { it == "rain" })
        }
    }

    @Test
    fun everyMoodAndBandHasCourses() {
        val none = CourseFilter()
        MOOD_KEYS.forEach { key ->
            val count = filterCourses(dummies, none.copy(mood = key), null, { factsOf(it).meters }, { emptyList() }).size
            assertTrue("$key $count", count >= 5)
        }
        LengthBand.entries.forEach { band ->
            val count = dummies.count { bandOf(factsOf(it).meters) == band }
            assertTrue("$band $count", count >= 10)
        }
    }

    @Test
    fun rainInEveryRegion() {
        dummies.groupBy { it.region }.forEach { (region, list) -> assertTrue(region, list.any { "rain" in it.tags.weathers }) }
    }

    @Test
    fun namesNotEmpty() {
        dummies.forEach { c ->
            assertTrue(c.id, c.name.isNotBlank() && c.summary.isNotBlank())
            assertTrue(c.id, c.sections.all { it.name.isNotBlank() })
        }
    }

    @Test
    fun realCoursesHaveSomeRain() {
        assertTrue(real.count { "rain" in it.tags.weathers } >= 3)
    }
}
