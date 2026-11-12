package com.byway.core.course

import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CourseFactsTest {
    private val straight = (0..100).map { GeoPoint(37.0, 127.0 + it * 0.001) }

    private fun course(path: List<GeoPoint>, elevation: List<Double> = emptyList(), sections: List<Section>? = null) =
        Course("c", "시험", path, sections ?: listOf(Section("전체", 0, path.lastIndex, "river")), elevation = elevation)

    @Test
    fun aStraightRoadHasARatioOfOne() {
        assertEquals(1.0, factsOf(course(straight)).eumRatio, 0.01)
    }

    @Test
    fun aRoadThatGoesThereAndBackAroundHasAHighRatio() {
        val around = (0..50).map { GeoPoint(37.0 + it * 0.001, 127.0) } +
            (0..50).map { GeoPoint(37.05, 127.0 + it * 0.001) } +
            (50 downTo 0).map { GeoPoint(37.0 + it * 0.001, 127.05) }
        assertTrue(factsOf(course(around)).eumRatio > 3.0)
    }

    @Test
    fun aStraightRoadHasNoCurves() {
        assertEquals(0, factsOf(course(straight)).curves)
    }

    @Test
    fun eachRightAngleCountsAsACurve() {
        val stairs = (0..5).flatMap { step ->
            val lat = 37.0 + step * 0.01
            val lon = 127.0 + step * 0.01
            (0..10).map { GeoPoint(lat, lon + it * 0.001) } + (0..10).map { GeoPoint(lat + it * 0.001, lon + 0.01) }
        }
        assertEquals(11, factsOf(course(stairs)).curves)
    }

    @Test
    fun climbAddsOnlyTheWayUp() {
        assertEquals(30, factsOf(course(straight, listOf(10.0, 30.0, 20.0, 30.0))).climbMeters)
    }

    @Test
    fun topIsTheHighestPoint() {
        assertEquals(30, factsOf(course(straight, listOf(10.0, 30.0, 20.0))).topMeters)
    }

    @Test
    fun aCourseWithoutElevationHasNoClimb() {
        assertEquals(0, factsOf(course(straight)).climbMeters)
    }

    @Test
    fun moodShareFollowsSectionLength() {
        val sections = listOf(Section("숲", 0, 24, "forest"), Section("강", 25, 100, "river"))
        val share = factsOf(course(straight, sections = sections)).moodShare
        assertEquals(25, share["forest"])
        assertEquals(75, share["river"])
    }

    @Test
    fun theDemoCourseMatchesItsMeasuredShape() {
        val demo = parseCourse(File("../app/src/main/assets/courses/paldang-sunset.json").readText())
        val facts = factsOf(demo)
        assertEquals(50.3, facts.meters / 1_000, 0.1)
        assertEquals(2.1, facts.eumRatio, 0.05)
        assertEquals(48, facts.curves)
    }
}
