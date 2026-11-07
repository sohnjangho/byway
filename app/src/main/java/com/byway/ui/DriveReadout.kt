package com.byway.ui

import com.byway.core.course.Course
import java.util.Locale

/** 주행 화면이 그리는 값. [filled]는 구간마다 지나온 비율이다. */
data class DriveReadout(val sectionIndex: Int, val remainingKm: String, val filled: List<Float>)

private val ENGINE_LINE = Regex("""^(.+) · 남은 거리 ([0-9.]+)km$""")

/**
 * [line]은 엔진이 만든 "구간 · 남은 거리 28.4km" 문구다. DriveState 에 구간과 거리 값이 따로 없어서 읽어 낸다.
 * 첫 문구가 오기 전에는 코스 이름만 있으므로 코스 출발점으로 본다.
 */
fun readoutOf(course: Course, sectionMeters: List<Double>, line: String): DriveReadout {
    val total = sectionMeters.sum()
    val match = ENGINE_LINE.matchEntire(line)
    val section = match?.let { found -> course.sections.indexOfFirst { it.name == found.groupValues[1] } } ?: 0
    val remainingKm = match?.groupValues?.get(2) ?: String.format(Locale.ROOT, "%.1f", total / 1_000)
    return DriveReadout(section.coerceAtLeast(0), remainingKm, filledShares(sectionMeters, total - remainingKm.toDouble() * 1_000))
}

private fun filledShares(sectionMeters: List<Double>, travelled: Double): List<Float> {
    var left = travelled
    return sectionMeters.map { meters ->
        val share = (left / meters).coerceIn(0.0, 1.0)
        left -= meters
        share.toFloat()
    }
}
