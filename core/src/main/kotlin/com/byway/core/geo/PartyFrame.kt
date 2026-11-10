package com.byway.core.geo

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/** 위도 1도의 길이(m). [GeoPoint.distanceTo]와 같은 지구 반지름으로 잰다. */
private val METERS_PER_DEGREE = Math.toRadians(1.0) * 6_371_000.0

/** 지도에 보여 줄 범위. 가운데와 폭·높이(m)로 둔다. */
data class GeoFrame(val center: GeoPoint, val widthMeters: Double, val heightMeters: Double) {
    private val metersPerLon get() = METERS_PER_DEGREE * cos(Math.toRadians(center.lat))

    val west: Double get() = center.lon - widthMeters / 2 / metersPerLon
    val east: Double get() = center.lon + widthMeters / 2 / metersPerLon
    val south: Double get() = center.lat - heightMeters / 2 / METERS_PER_DEGREE
    val north: Double get() = center.lat + heightMeters / 2 / METERS_PER_DEGREE

    /** 가운데에서 반폭·반높이의 [inner]배 안에 있는지. */
    fun contains(point: GeoPoint, inner: Double = 1.0): Boolean {
        val dx = (point.lon - center.lon) * metersPerLon
        val dy = (point.lat - center.lat) * METERS_PER_DEGREE
        return abs(dx) <= widthMeters / 2 * inner && abs(dy) <= heightMeters / 2 * inner
    }
}

/** 파티 확대 지도의 값. 주행 화면 3분할 설계 3절. */
data class FrameRules(
    /** 내 차에서 이 거리 안의 멤버만 확대 범위에 넣는다. 더 먼 멤버는 코스 전체 지도에서도 따로 보인다. */
    val reachMeters: Double = 5_000.0,
    /** 점들을 담은 상자에 사방으로 더하는 여백(폭의 비율). */
    val margin: Double = 0.2,
    /** 가장 좁은 폭. 멤버가 바로 옆에 있어도 너무 확대하지 않는다. */
    val minMeters: Double = 1_500.0,
    /** 점이 이 안쪽 비율 밖으로 나가면 범위를 다시 잡는다. */
    val inner: Double = 0.8,
    /** 점들이 범위의 이 비율보다 좁게 모이면 다시 잡아 확대한다. */
    val tight: Double = 0.35,
)

/** [far]는 범위에 넣지 않은 먼 멤버 수다. */
data class PartyFrame(val frame: GeoFrame, val far: Int)

/**
 * 내 차와 가까운 멤버를 모두 담는 범위. [aspect]는 지도 칸의 가로/세로다.
 * 주행 화면 규칙(움직이는 것은 위치 점뿐)을 지키려고 범위는 고정해 두고, 점이 안쪽 밖으로 나가거나 크게 모였을 때만 [current]를 바꾼다.
 * 칸 크기를 아직 모르거나 가까운 멤버가 없으면 null 이다.
 */
fun partyFrame(me: GeoPoint, members: List<GeoPoint>, aspect: Double, current: GeoFrame?, rules: FrameRules = FrameRules()): PartyFrame? {
    if (!(aspect > 0.0) || aspect.isInfinite()) return null
    val near = members.filter { me.distanceTo(it) <= rules.reachMeters }
    if (near.isEmpty()) return null
    val far = members.size - near.size
    val points = near + me
    val box = Box.of(me, points)
    if (current != null && keeps(current, points, box, aspect, rules)) return PartyFrame(current, far)
    return PartyFrame(fit(me, box, aspect, rules), far)
}

/** 내 차를 원점으로 한 평면 상자(m). */
private data class Box(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
    val width get() = maxX - minX
    val height get() = maxY - minY

    companion object {
        fun of(origin: GeoPoint, points: List<GeoPoint>): Box {
            val perLon = METERS_PER_DEGREE * cos(Math.toRadians(origin.lat))
            val xs = points.map { (it.lon - origin.lon) * perLon }
            val ys = points.map { (it.lat - origin.lat) * METERS_PER_DEGREE }
            return Box(xs.min(), xs.max(), ys.min(), ys.max())
        }
    }
}

private fun keeps(current: GeoFrame, points: List<GeoPoint>, box: Box, aspect: Double, rules: FrameRules): Boolean {
    val sameShape = abs(current.widthMeters / current.heightMeters - aspect) <= aspect * 0.01
    val inside = points.all { current.contains(it, rules.inner) }
    val gathered = box.width < rules.tight * current.widthMeters &&
        box.height < rules.tight * current.heightMeters &&
        current.widthMeters > rules.minMeters + 1
    return sameShape && inside && !gathered
}

private fun fit(origin: GeoPoint, box: Box, aspect: Double, rules: FrameRules): GeoFrame {
    var width = box.width * (1 + 2 * rules.margin)
    val height = box.height * (1 + 2 * rules.margin)
    width = max(width, height * aspect)
    width = max(width, rules.minMeters)
    val perLon = METERS_PER_DEGREE * cos(Math.toRadians(origin.lat))
    val center = GeoPoint(
        origin.lat + (box.minY + box.maxY) / 2 / METERS_PER_DEGREE,
        origin.lon + (box.minX + box.maxX) / 2 / perLon,
    )
    return GeoFrame(center, width, width / aspect)
}
