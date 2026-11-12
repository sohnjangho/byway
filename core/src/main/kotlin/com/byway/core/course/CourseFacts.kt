package com.byway.core.course

import com.byway.core.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SAMPLE_METERS = 100.0
private const val CURVE_DEGREES = 45.0
private const val CURVE_WINDOW = 3

/** 코스 선과 고도에서 계산한 길의 성격. 코스마다 다른 특색을 사람이 고르지 않고 수치로 보여 준다. */
data class CourseFacts(
    val meters: Double,
    val directMeters: Double,
    /** 출발과 도착의 직선거리보다 몇 배 돌아가는지. */
    val eumRatio: Double,
    val curves: Int,
    val climbMeters: Int,
    val topMeters: Int,
    /** 구간 분위기별 거리 비율(%). */
    val moodShare: Map<String, Int>,
)

fun factsOf(course: Course): CourseFacts {
    val path = course.path
    val meters = lengthOf(path)
    val direct = path.first().distanceTo(path.last())
    return CourseFacts(
        meters = meters,
        directMeters = direct,
        eumRatio = if (direct > 0) meters / direct else 1.0,
        curves = countCurves(path),
        climbMeters = course.elevation.zipWithNext { a, b -> maxOf(0.0, b - a) }.sum().roundToInt(),
        topMeters = (course.elevation.maxOrNull() ?: 0.0).roundToInt(),
        moodShare = moodShare(course, meters),
    )
}

private fun lengthOf(path: List<GeoPoint>) = path.zipWithNext { a, b -> a.distanceTo(b) }.sum()

private fun moodShare(course: Course, total: Double): Map<String, Int> {
    val meters = mutableMapOf<String, Double>()
    for (section in course.sections) {
        val mood = section.mood ?: continue
        val end = minOf(section.endIndex + 1, course.path.lastIndex)
        meters[mood] = (meters[mood] ?: 0.0) + lengthOf(course.path.subList(section.startIndex, end + 1))
    }
    return meters.mapValues { (it.value / total * 100).roundToInt() }
}

/** 100m 간격으로 다시 찍고, 300m 사이에 45도 넘게 꺾이면 굽이 하나로 센다. */
private fun countCurves(path: List<GeoPoint>): Int {
    val samples = resample(path)
    val headings = samples.zipWithNext { a, b -> heading(a, b) }
    var count = 0
    var i = 0
    while (i + CURVE_WINDOW < headings.size) {
        if (abs(turn(headings[i], headings[i + CURVE_WINDOW])) >= CURVE_DEGREES) {
            count++
            i += CURVE_WINDOW + 1
        } else {
            i++
        }
    }
    return count
}

private fun resample(path: List<GeoPoint>): List<GeoPoint> {
    val samples = mutableListOf(path.first())
    var carried = 0.0
    for ((a, b) in path.zipWithNext()) {
        val length = a.distanceTo(b)
        if (length == 0.0) continue
        var next = SAMPLE_METERS - carried
        while (next <= length) {
            val t = next / length
            samples += GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            next += SAMPLE_METERS
        }
        carried = (carried + length) % SAMPLE_METERS
    }
    return samples
}

private fun heading(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return Math.toDegrees(atan2(y, x))
}

private fun turn(from: Double, to: Double): Double = ((to - from + 540) % 360) - 180
