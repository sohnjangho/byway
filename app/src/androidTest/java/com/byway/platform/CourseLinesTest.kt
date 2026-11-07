package com.byway.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import com.byway.service.MapColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@RunWith(AndroidJUnit4::class)
class CourseLinesTest {
    private val path = (0..5).map { GeoPoint(37.0 + it / 100.0, 127.0) }
    private val COLORS = MapColors(forest = 0xFF112233.toInt(), city = 0xFF445566.toInt(), river = 0xFF778899.toInt(), outline = 0, blank = 0)

    private fun courseOf(vararg sections: Section) = Course("c", "코스", path, sections.toList())

    private fun Feature.points(): List<Point> = (geometry() as LineString).coordinates()

    private fun lines(vararg sections: Section) = courseLines(courseOf(*sections), COLORS).features().orEmpty()

    @Test
    fun eachSectionIsPaintedInItsMoodColor() {
        val colors = lines(
            Section("숲", 0, 1, "forest"),
            Section("도심", 2, 3, "city"),
            Section("강", 4, 5, "river"),
        ).map { it.getStringProperty("color") }

        assertEquals(listOf("#112233", "#445566", "#778899"), colors)
    }

    @Test
    fun aSectionWithoutMoodStillGetsAColor() {
        assertEquals("#445566", lines(Section("구간", 0, 5)).single().getStringProperty("color"))
    }

    @Test
    fun neighbouringSectionsMeetWithoutAGap() {
        val (first, second) = lines(Section("숲", 0, 2, "forest"), Section("강", 3, 5, "river"))

        assertEquals(first.points().last(), second.points().first())
    }

    @Test
    fun theLastSectionEndsAtTheEndOfTheCourse() {
        val line = lines(Section("숲", 0, 2, "forest"), Section("강", 3, 5, "river")).last()

        assertEquals(Point.fromLngLat(path.last().lon, path.last().lat), line.points().last())
    }
}
