package com.byway.core.course

import kotlin.math.roundToInt

/** 코스를 달리는 세 가지 예상 시간(분). 측정값이 아니라 길이와 굽이, 들를 곳에서 어림한 값이다. 교통 상황은 넣지 않는다. */
data class Pace(
    /** 쉬지 않고 달린다. */
    val light: Int,
    /** 경치 좋은 곳에 두 번 잠깐 선다. */
    val rest: Int,
    /** 조금 천천히 달리며 코스 주변을 다 들르고 식사를 한 번 한다. */
    val stay: Int,
)

private const val BASE_KMH = 55.0
private const val KMH_PER_CURVE_DENSITY = 12.0
private const val MAX_CURVE_DENSITY = 1.5
private const val VIEW_MINUTES = 10
private const val SIGHT_MINUTES = 20
private const val MEAL_MINUTES = 40
private const val REST_VIEWS = 2
private const val STAY_SLOWDOWN = 1.1
private const val STEP_MINUTES = 5

/** [kinds]는 코스 주변 정보의 종류다. view·photo 는 경치, sight 는 명소, food 는 음식이다. */
fun paceOf(meters: Double, curves: Int, kinds: List<String>): Pace {
    val km = meters / 1_000
    if (km <= 0) return Pace(STEP_MINUTES, STEP_MINUTES, STEP_MINUTES)
    // 굽이가 많을수록 느리게 잡는다. 1km 에 굽이 1.5개가 넘으면 더 느려지지 않는다.
    val kmh = BASE_KMH - KMH_PER_CURVE_DENSITY * minOf(curves / km, MAX_CURVE_DENSITY)
    val drive = km / kmh * 60
    val views = kinds.count { it == "view" || it == "photo" }
    val sights = kinds.count { it == "sight" }
    val meal = if ("food" in kinds) MEAL_MINUTES else 0
    return Pace(
        light = toStep(drive),
        rest = toStep(drive + VIEW_MINUTES * minOf(views, REST_VIEWS)),
        stay = toStep(drive * STAY_SLOWDOWN + VIEW_MINUTES * views + SIGHT_MINUTES * sights + meal),
    )
}

private fun toStep(minutes: Double): Int = maxOf(STEP_MINUTES, (minutes / STEP_MINUTES).roundToInt() * STEP_MINUTES)
