package com.byway.platform

import com.byway.core.course.Course
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal const val LINE_COLOR = "color"

private const val PLAIN_COLOR = "#A3B3D1"
private val MOOD_COLORS = mapOf("forest" to "#7CC48F", "city" to PLAIN_COLOR, "river" to "#5FC6D8")

/** 구간마다 선 하나를 만든다. 다음 구간의 첫 점까지 이어야 구간 사이가 끊겨 보이지 않는다. */
internal fun courseLines(course: Course): FeatureCollection =
    FeatureCollection.fromFeatures(
        course.sections.map { section ->
            val end = minOf(section.endIndex + 1, course.path.lastIndex)
            val points = course.path.subList(section.startIndex, end + 1).map { Point.fromLngLat(it.lon, it.lat) }
            Feature.fromGeometry(LineString.fromLngLats(points)).apply {
                addStringProperty(LINE_COLOR, MOOD_COLORS[section.mood] ?: PLAIN_COLOR)
            }
        },
    )
