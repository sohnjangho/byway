package com.byway.server

import com.byway.core.geo.GeoPoint

/**
 * 시연용 가상 멤버(공유 설계 6-5). 에뮬레이터가 1대라 다른 차를 서버가 대신 움직인다.
 * 실제 차 위치가 오기 전에는 출발점에서 시속 60km로 나아가고(시연용 2는 1km 뒤에서 따라온다),
 * 위치가 오면 실제 차의 코스 위 자리보다 1km 앞(시연용 1)과 1km 뒤(시연용 2)에 놓인다.
 */
class DemoMembers(private val path: List<GeoPoint>) {
    private val along: List<Double> = path.indices.runningFold(0.0) { sum, i -> if (i == 0) sum else sum + path[i - 1].distanceTo(path[i]) }.drop(1)
    private val total = along.last()

    fun places(count: Int, sinceOpenMs: Long, car: GeoPoint?): List<GeoPoint> {
        val base = car?.let(::alongOf) ?: (sinceOpenMs.coerceAtLeast(0) / 1000.0 * SPEED_MPS)
        val offsets = if (car == null) listOf(0.0, -GAP_METERS) else listOf(GAP_METERS, -GAP_METERS)
        return (0 until count).map { i -> pointAt(base + offsets[i % offsets.size]) }
    }

    private fun alongOf(point: GeoPoint): Double = along[path.indices.minBy { path[it].distanceTo(point) }]

    private fun pointAt(meters: Double): GeoPoint {
        val m = meters.coerceIn(0.0, total)
        val i = along.indexOfFirst { it >= m }.coerceAtLeast(1)
        val span = along[i] - along[i - 1]
        val t = if (span == 0.0) 0.0 else (m - along[i - 1]) / span
        val a = path[i - 1]
        val b = path[i]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    companion object {
        const val SPEED_MPS = 60_000.0 / 3600.0
        const val GAP_METERS = 1_000.0
        fun nameOf(i: Int) = "시연용 ${i + 1}"
        fun idOf(i: Int) = "demo-${i + 1}"
        fun avatarOf(i: Int) = "avatar-%02d".format(9 + i % 2)
    }
}
