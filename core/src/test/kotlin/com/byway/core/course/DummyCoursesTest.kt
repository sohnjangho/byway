package com.byway.core.course

import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyCoursesTest {
    private fun file(km: Double = 15.0) = """
        [{"id":"dummy-test-1","name":"시험 더미","region":"수도권","summary":"시험용 길",
          "km":$km,
          "points":[{"name":"시청","lat":37.5663,"lon":126.9779},
                    {"name":"동쪽","lat":37.5663,"lon":127.0279},
                    {"name":"북쪽","lat":37.6163,"lon":127.0279}],
          "tags":{"themes":["river"],"moods":["alone"],"times":["day"],"seasons":["spring"],"weathers":["rain"]}}]
    """

    private fun straight(path: List<GeoPoint>) = path.zipWithNext { a, b -> a.distanceTo(b) }.sum()

    @Test
    fun pointsBecomeDenseStraightPath() {
        val course = parseDummyCourses(file()).single()
        assertEquals(GeoPoint(37.5663, 126.9779), course.path.first())
        assertEquals(GeoPoint(37.6163, 127.0279), course.path.last())
        assertTrue(course.path.zipWithNext { a, b -> a.distanceTo(b) }.all { it <= DUMMY_STEP_METERS + 1e-6 })
        assertTrue(course.dummy)
        assertEquals(15_000.0, course.declaredMeters!!, 1e-9)
        assertEquals(listOf("rain"), course.tags.weathers)
        assertEquals("수도권", course.region)
    }

    @Test
    fun oneSectionPerLegNamedByItsEnd() {
        val course = parseDummyCourses(file()).single()
        assertEquals(listOf("동쪽", "북쪽"), course.sections.map { it.name })
        assertEquals(0, course.sections.first().startIndex)
        assertEquals(course.path.lastIndex, course.sections.last().endIndex)
        course.sections.zipWithNext { a, b -> assertEquals(a.endIndex + 1, b.startIndex) }
        assertEquals(GeoPoint(37.5663, 127.0279), course.path[course.sections.first().endIndex])
        assertTrue(course.sections.all { it.name.isNotBlank() })
    }

    @Test
    fun lengthScaleStretchesToDeclared() {
        val line = straight(parseDummyCourses(file()).single().path)
        val course = parseDummyCourses(file(km = line * 1.5 / 1000)).single()
        assertEquals(1.5, course.lengthScale(), 0.01)
        val real = course.copy(dummy = false, declaredMeters = null)
        assertEquals(1.0, real.lengthScale(), 1e-9)
    }

    @Test
    fun factsUseDeclaredMeters() {
        val course = parseDummyCourses(file(km = 42.0)).single()
        assertEquals(42_000.0, factsOf(course).meters, 1e-9)
    }
}
