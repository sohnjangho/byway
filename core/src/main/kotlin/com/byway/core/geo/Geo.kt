package com.byway.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

data class GeoPoint(val lat: Double, val lon: Double) {
    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    /** 코스 구간 길이에서는 평면 근사로 충분하다. 이 점을 원점으로 미터 좌표를 만든다. */
    fun distanceToSegment(a: GeoPoint, b: GeoPoint): Double {
        val metersPerLon = Math.toRadians(1.0) * EARTH_RADIUS_METERS * cos(Math.toRadians(lat))
        val metersPerLat = Math.toRadians(1.0) * EARTH_RADIUS_METERS
        val ax = (a.lon - lon) * metersPerLon
        val ay = (a.lat - lat) * metersPerLat
        val bx = (b.lon - lon) * metersPerLon
        val by = (b.lat - lat) * metersPerLat
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        return sqrt(px * px + py * py)
    }
}
