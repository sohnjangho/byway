package com.byway.platform

import com.byway.core.course.Course
import com.byway.service.MapColors
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal const val LINE_COLOR = "color"

private fun MapColors.of(mood: String?) = when (mood) {
    "forest" -> forest
    "river" -> river
    else -> city
}

private fun hex(color: Int) = String.format("#%06X", color and 0xFFFFFF)

/** 구간마다 선 하나를 만든다. 다음 구간의 첫 점까지 이어야 구간 사이가 끊겨 보이지 않는다. */
internal fun courseLines(course: Course, colors: MapColors): FeatureCollection =
    FeatureCollection.fromFeatures(
        course.sections.map { section ->
            val end = minOf(section.endIndex + 1, course.path.lastIndex)
            val points = course.path.subList(section.startIndex, end + 1).map { Point.fromLngLat(it.lon, it.lat) }
            Feature.fromGeometry(LineString.fromLngLats(points)).apply {
                addStringProperty(LINE_COLOR, hex(colors.of(section.mood)))
            }
        },
    )
