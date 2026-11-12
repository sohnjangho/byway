package com.byway.ui

import com.byway.core.course.MoodLook
import com.byway.core.course.Px
import com.byway.core.course.SceneMood
import kotlin.math.roundToInt

fun moodKey(mood: SceneMood): String? = if (mood == SceneMood.PLAIN) null else mood.name.lowercase()

/** 효과 무늬가 [seconds]초에 옮겨 간 거리. [layer] 0은 앞 층, 1은 뒤 층이다. */
fun tileShift(look: MoodLook, seconds: Float, layer: Int): Px {
    val period = if (layer == 0) look.near else look.far
    if (period <= 0f) return Px(0f, 0f)
    val progress = (seconds % period) / period
    return Px(look.dx * progress, look.dy * progress)
}

/** 시안 값의 색 표기 두 가지(#rrggbb, rgba(r, g, b, a))를 읽는다. */
fun parseRgba(text: String): Int {
    if (text.startsWith("#")) return (0xFF shl 24) or text.drop(1).toInt(16)
    val (r, g, b, a) = text.substringAfter("(").substringBefore(")").split(",").map { it.trim().toFloat() }
    return ((a * 255).roundToInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
}
