package com.byway.ui

private val SCALE_STEPS = listOf(100, 200, 500, 1_000, 2_000, 5_000)

/** 파티 확대 지도의 축척 막대 길이(m). 지도 폭의 4분의 1 안에서 가장 긴 깔끔한 길이다. */
fun scaleBarMeters(frameWidthMeters: Double): Int =
    SCALE_STEPS.lastOrNull { it <= frameWidthMeters / 4 } ?: SCALE_STEPS.first()

fun scaleLabel(meters: Int): String = if (meters < 1_000) "$meters m" else "${meters / 1_000} km"
