package com.byway.ui

import com.byway.core.course.FrameInfo
import com.byway.core.course.Px
import com.byway.core.course.TrackPoint
import java.util.Locale
import kotlin.math.abs

/** 홈 아래 띠가 보여 주는 것. */
enum class StripMode { SCRUB, COURSES }

/** 훑어보기 자료가 없는 코스는 다른 코스 목록만 보인다. */
fun stripMode(wantsScrub: Boolean, hasFrames: Boolean): StripMode =
    if (wantsScrub && hasFrames) StripMode.SCRUB else StripMode.COURSES

/** 사진 띠를 민 거리에서 코스 위 거리(km)를 구한다. [filmPx]는 사진 띠 전체 길이다. */
fun scrubKm(scrollX: Int, filmPx: Int, totalKm: Double): Double =
    if (filmPx <= 0) 0.0 else (scrollX.toDouble() / filmPx).coerceIn(0.0, 1.0) * totalKm

/** 코스 위 거리에 해당하는 장면 그림 위의 자리. */
fun trackAt(track: List<TrackPoint>, km: Double): Px? {
    if (track.isEmpty()) return null
    val after = track.indexOfFirst { it.km >= km }
    if (after <= 0) return (if (after == 0) track.first() else track.last()).let { Px(it.x, it.y) }
    val a = track[after - 1]
    val b = track[after]
    val t = if (b.km > a.km) ((km - a.km) / (b.km - a.km)).toFloat() else 0f
    return Px(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

/** 그 거리에 가장 가까운 사진의 차례. 사진이 없으면 -1 이다. */
fun frameAt(frames: List<FrameInfo>, km: Double): Int =
    frames.indices.minByOrNull { abs(frames[it].km - km) } ?: -1

fun scrubKmText(km: Double): String = String.format(Locale.ROOT, "%.1fkm 지점", km)

fun scrubWhere(frame: FrameInfo, placeName: String?): String =
    listOfNotNull(frame.section, frame.elev?.let { "해발 ${it}m" }, placeName?.let { "$it 근처" }).joinToString(" · ")
