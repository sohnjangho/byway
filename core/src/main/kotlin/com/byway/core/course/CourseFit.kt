package com.byway.core.course

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// 코스를 고를 때 보는 값. 측정값이 아니라 코스 자료와 날짜로 계산한 예상이다. 화면에도 예상이라고 적는다.

/** 그날의 일출과 일몰. 그 지역 시각의 자정부터 센 분이다. */
data class SunTimes(val riseMinute: Int, val setMinute: Int)

private const val KOREA_UTC_OFFSET_MINUTES = 540
private const val DAYS_IN_YEAR = 365
private const val MINUTES_IN_DAY = 1440

/** 태양 고도가 지평선 아래 0.833도일 때를 일출·일몰로 본다(NOAA 근사식). 몇 분 안쪽이면 충분하다. */
fun sunTimes(dayOfYear: Int, lat: Double, lon: Double, utcOffsetMinutes: Int = KOREA_UTC_OFFSET_MINUTES): SunTimes {
    val g = 2 * Math.PI / DAYS_IN_YEAR * (dayOfYear - 1)
    val equationOfTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
    val declination = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
        0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
    val latitude = Math.toRadians(lat)
    val ratio = cos(Math.toRadians(90.833)) / (cos(latitude) * cos(declination)) - tan(latitude) * tan(declination)
    val hourAngle = Math.toDegrees(acos(ratio.coerceIn(-1.0, 1.0)))
    val noon = 720 - 4 * lon - equationOfTime + utcOffsetMinutes
    return SunTimes((noon - 4 * hourAngle).roundToInt(), (noon + 4 * hourAngle).roundToInt())
}

/** 계절의 절정일(1년 중 며칠째). 봄꽃 4월 10일, 한여름 7월 20일, 단풍 10월 25일, 한겨울 1월 20일이다. */
private val SEASON_PEAK = mapOf("spring" to 100, "summer" to 201, "autumn" to 298, "winter" to 20)
private const val PEAK_DAYS = 20
private const val FARTHEST_DAYS = 182.5
private const val LOWEST_FIT = 20.0
private const val FIT_STEP = 5

/** 코스의 추천 계절이 오늘과 얼마나 맞는지(%). 절정 앞뒤 20일은 100이고 반년 떨어지면 20이다. 계절이 없으면 null 이다. */
fun seasonFit(seasons: List<String>, dayOfYear: Int): Int? =
    seasons.mapNotNull(SEASON_PEAK::get).maxOfOrNull { peak ->
        val apart = abs(dayOfYear - peak).let { min(it, DAYS_IN_YEAR - it) }
        val past = (apart - PEAK_DAYS).coerceAtLeast(0)
        stepped(100 - past / (FARTHEST_DAYS - PEAK_DAYS) * (100 - LOWEST_FIT))
    }

/** 지금 출발해 달리는 시간 중 코스의 추천 시간대와 겹치는 비율(%). 시간대가 없으면 null 이다. */
fun timeFit(times: List<String>, startMinute: Int, durationMinutes: Int, sun: SunTimes): Int? {
    // 시간대에 따라 일출·일몰이 하루 경계를 넘을 수 있어 하루 안의 분으로 맞춘다.
    val windows = times.mapNotNull { timeWindow(it, sun) }.map { (from, to) -> from.mod(MINUTES_IN_DAY) to to.mod(MINUTES_IN_DAY) }
    if (windows.isEmpty() || durationMinutes <= 0) return null
    val inside = (0 until durationMinutes).count { offset ->
        val minute = (startMinute + offset) % MINUTES_IN_DAY
        windows.any { (from, to) -> if (from <= to) minute in from until to else minute >= from || minute < to }
    }
    return stepped(inside * 100.0 / durationMinutes)
}

/** 낮은 해가 뜨고 30분 뒤부터 지기 30분 전까지, 노을은 일몰 1시간 전부터 20분 뒤까지, 일출은 30분 전부터 1시간 뒤까지다. */
private fun timeWindow(time: String, sun: SunTimes): Pair<Int, Int>? = when (time) {
    "day" -> sun.riseMinute + 30 to sun.setMinute - 30
    "sunset" -> sun.setMinute - 60 to sun.setMinute + 20
    "sunrise" -> sun.riseMinute - 30 to sun.riseMinute + 60
    "night" -> sun.setMinute + 40 to sun.riseMinute - 40
    else -> null
}

private fun stepped(value: Double): Int = (value / FIT_STEP).roundToInt() * FIT_STEP

private const val CURVE_WEIGHT = 0.5
private const val CLIMB_WEIGHT = 0.2
private const val DETOUR_WEIGHT = 0.15
private const val AWAY_WEIGHT = 0.15
private const val FULL_CURVES_PER_KM = 1.2
private const val FULL_CLIMB_PER_KM = 25.0
private const val FULL_DETOUR = 2.0

/**
 * 운전 즐거움(0~100점). km당 커브, km당 오르내림, 돌아가는 정도, 도심 밖 비율로 낸다. 코스 길이는 점수에 넣지 않는다.
 * 사람의 평가가 아니라 길의 모양에서 계산한 값이다. 고도 자료가 없으면 그 항목을 빼고 낸다.
 */
fun funScore(facts: CourseFacts, hasElevation: Boolean): Int {
    val km = facts.meters / 1000
    if (km <= 0) return 0
    val parts = buildList {
        add(CURVE_WEIGHT to min(facts.curves / km / FULL_CURVES_PER_KM, 1.0))
        if (hasElevation) add(CLIMB_WEIGHT to min(facts.climbMeters / km / FULL_CLIMB_PER_KM, 1.0))
        add(DETOUR_WEIGHT to ((facts.eumRatio - 1) / FULL_DETOUR).coerceIn(0.0, 1.0))
        add(AWAY_WEIGHT to (1 - (facts.moodShare["city"] ?: 0) / 100.0).coerceIn(0.0, 1.0))
    }
    return (parts.sumOf { (weight, value) -> weight * value } / parts.sumOf { it.first } * 100).roundToInt()
}

/** 차의 평균 효율. 연료차는 km/L, 전기차는 km/kWh 다. */
data class EnergyRate(val electric: Boolean, val kmPerUnit: Double)

private val FUEL_KM_PER_LITRE = 4.0..40.0
private val EV_KM_PER_KWH = 2.0..12.0

/** 차가 알려 준 효율을 믿을 만한 범위에서만 쓴다. 단위가 다르거나 값이 쌓이기 전이면 엉뚱한 양을 보이느니 숨긴다. */
fun plausibleRate(electric: Boolean, value: Double?): EnergyRate? {
    val range = if (electric) EV_KM_PER_KWH else FUEL_KM_PER_LITRE
    return value?.takeIf { it in range }?.let { EnergyRate(electric, it) }
}

/** 코스를 달리는 데 드는 연료(L)나 전기(kWh). */
fun energyNeeded(meters: Double, rate: EnergyRate): Double = meters / 1000 / rate.kmPerUnit

/** 전망이 좋은 곳과 사진 찍기 좋은 곳의 수. */
fun viewCount(course: Course): Int = course.spots.count { it.kind == "view" || it.kind == "photo" }

/** 도심 밖을 달리는 거리 비율(%). */
fun naturePercent(moodShare: Map<String, Int>): Int = moodShare.filterKeys { it != "city" }.values.sum().coerceAtMost(100)
