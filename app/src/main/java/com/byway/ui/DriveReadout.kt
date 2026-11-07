package com.byway.ui

import java.util.Locale

/** 주행 화면이 그리는 값. [filled]는 구간마다 지나온 비율이다. */
data class DriveReadout(val sectionIndex: Int, val remainingKm: String, val filled: List<Float>)

/** [remainingMeters]가 null 이면 첫 위치가 오기 전이므로 코스 전체가 남았다. */
fun readoutOf(sectionMeters: List<Double>, sectionIndex: Int, remainingMeters: Double?): DriveReadout {
    val total = sectionMeters.sum()
    val remaining = remainingMeters ?: total
    val km = String.format(Locale.ROOT, "%.1f", remaining / 1_000)
    return DriveReadout(sectionIndex, km, filledShares(sectionMeters, total - remaining))
}

private fun filledShares(sectionMeters: List<Double>, travelled: Double): List<Float> {
    var left = travelled
    return sectionMeters.map { meters ->
        val share = (left / meters).coerceIn(0.0, 1.0)
        left -= meters
        share.toFloat()
    }
}
