package com.byway.core.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** [GeoFrame]과 같은 지구 반지름. 범위의 미터를 그대로 화면에 맞춘다. */
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val TILE_PX = 256.0

/** 웹 메르카토르의 세계 좌표(0~1). 지도 조각이 이 좌표로 나뉜다. */
fun worldX(lon: Double): Double = (lon + 180) / 360

fun worldY(lat: Double): Double {
    val s = sin(Math.toRadians(lat))
    return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
}

/** 화면 위 직사각형(px). */
data class Viewport(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * 범위를 [fit] 칸에 맞추는 웹 메르카토르 투영(주행 화면 홈 맞춤 설계 2절).
 * 조각과 같은 투영이라 코스 선과 지도가 어긋나지 않는다. [fit] 밖(글 칸 아래)도 같은 식으로 이어진다.
 */
class MapProjection(frame: GeoFrame, fit: Viewport) {
    private val originX = worldX(frame.center.lon)
    private val originY = worldY(frame.center.lat)
    private val centerX = fit.left + fit.width / 2.0
    private val centerY = fit.top + fit.height / 2.0
    private val metersPerWorld = 2 * PI * EARTH_RADIUS_METERS * cos(Math.toRadians(frame.center.lat))

    /** 세계 좌표 1이 화면에서 몇 px인지. */
    val pxPerWorld: Double = minOf(
        fit.width / (frame.widthMeters / metersPerWorld),
        fit.height / (frame.heightMeters / metersPerWorld),
    )

    val metersPerPx: Double get() = metersPerWorld / pxPerWorld

    fun x(p: GeoPoint): Float = (centerX + (worldX(p.lon) - originX) * pxPerWorld).toFloat()

    fun y(p: GeoPoint): Float = (centerY + (worldY(p.lat) - originY) * pxPerWorld).toFloat()

    /** 화면 [viewW]×[viewH]를 덮는 줌 [z]의 조각과 그 자리. */
    fun tilesFor(viewW: Float, viewH: Float, z: Int): List<TileSpot> {
        val n = 2.0.pow(z)
        val last = n.toInt() - 1
        fun index(world: Double) = floor(world * n).toInt().coerceIn(0, last)
        val firstX = index(originX - centerX / pxPerWorld)
        val lastX = index(originX + (viewW - centerX) / pxPerWorld)
        val firstY = index(originY - centerY / pxPerWorld)
        val lastY = index(originY + (viewH - centerY) / pxPerWorld)
        val size = (pxPerWorld / n).toFloat()
        return (firstY..lastY).flatMap { ty ->
            (firstX..lastX).map { tx ->
                TileSpot(
                    z, tx, ty,
                    (centerX + (tx / n - originX) * pxPerWorld).toFloat(),
                    (centerY + (ty / n - originY) * pxPerWorld).toFloat(),
                    size,
                )
            }
        }
    }
}

/** 조각 하나와 화면 위 자리(왼쪽 위, 한 변). */
data class TileSpot(val z: Int, val x: Int, val y: Int, val left: Float, val top: Float, val size: Float)

/** 조각 1px이 화면 1px에 가장 가까운 줌. 받는 양을 묶으려고 [min]~[max] 안에 둔다. */
fun tileZoom(pxPerWorld: Double, min: Int = 10, max: Int = 15): Int =
    (ln(pxPerWorld / TILE_PX) / ln(2.0)).roundToInt().coerceIn(min, max)

/** 코스 시작에서 [km] 달린 자리. 길이를 넘으면 끝 점이다. */
fun pointAt(path: List<GeoPoint>, km: Double): GeoPoint? {
    if (path.isEmpty()) return null
    var left = km * 1000
    path.zipWithNext { a, b ->
        val step = a.distanceTo(b)
        if (left <= step) {
            val t = if (step > 0) left / step else 0.0
            return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        }
        left -= step
    }
    return path.last()
}
