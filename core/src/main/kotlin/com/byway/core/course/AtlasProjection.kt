package com.byway.core.course

import com.byway.core.geo.GeoPoint
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

// 한국 지도 그림과 같은 자리 계산(tools/make-atlas.py). 줌 9 웹 메르카토르이고 원점은 북위 38.9도, 동경 123.6도다.
// 미리 그린 자리가 없는 코스(직접 만든 코스, 공유 코스)를 지도에 올릴 때 쓴다.
private const val ATLAS_ZOOM = 9
private const val ATLAS_NORTH = 38.9
private const val ATLAS_WEST = 123.6
private const val LINE_EVERY = 2

private fun pixel(lat: Double, lon: Double): Pair<Double, Double> {
    val size = 256.0 * 2.0.pow(ATLAS_ZOOM)
    val s = sin(lat * PI / 180)
    return (lon + 180) / 360 * size to (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * size
}

private val origin = pixel(ATLAS_NORTH, ATLAS_WEST)

fun toAtlas(point: GeoPoint): Px {
    val (x, y) = pixel(point.lat, point.lon)
    return Px((x - origin.first).toFloat(), (y - origin.second).toFloat())
}

/** 장면 그림 위의 자리. [frame]은 그 장면이 한국 지도에서 차지하는 자리(AtlasSpot.frame)다. 파티 핀을 장면 위에 찍을 때 쓴다. */
fun scenePointOf(point: GeoPoint, frame: Box, sceneSize: Px): Px {
    val at = toAtlas(point)
    return Px((at.x - frame.x) / frame.w * sceneSize.x, (at.y - frame.y) / frame.h * sceneSize.y)
}

/** 코스의 지도 자리. 장면 그림이 없으므로 [AtlasSpot.frame]은 코스 상자와 같다. */
fun atlasSpotOf(course: Course): AtlasSpot {
    val points = course.path.map(::toAtlas)
    val middle = toAtlas(GeoPoint(course.path.map { it.lat }.average(), course.path.map { it.lon }.average()))
    val left = floor(points.minOf { it.x })
    val top = floor(points.minOf { it.y })
    val box = Box(left, top, ceil(points.maxOf { it.x }) - left, ceil(points.maxOf { it.y }) - top)
    val lines = course.sections.map { section ->
        val part = points.subList(section.startIndex, minOf(section.endIndex + 2, points.size))
        SceneLine(section.mood ?: "", section.name, part.filterIndexed { i, _ -> i % LINE_EVERY == 0 } + part.last())
    }
    return AtlasSpot(middle.x, middle.y, box, box, lines)
}
