package com.byway.core.course

/** 바깥 날씨. 알 수 없으면 시간과 계절로만 판단한다. */
enum class Weather { CLEAR, CLOUDY, FOG, RAIN, SNOW, UNKNOWN }

/** 메인 화면의 분위기. */
enum class SceneMood { RAIN, SNOW, FOG, CLOUDY, SUNRISE, SUNSET, NIGHT, SPRING, SUMMER, AUTUMN, WINTER, PLAIN }

/** 달마다 해가 뜨고 지는 시(한국 기준 어림값). 일출·일몰 시각을 받으면 그것으로 바꾼다. */
private val SUNRISE_HOUR = listOf(7, 7, 6, 6, 5, 5, 5, 6, 6, 6, 7, 7)
private val SUNSET_HOUR = listOf(17, 18, 18, 19, 19, 19, 19, 19, 18, 18, 17, 17)

/** 날씨가 가장 먼저고 그다음 시간대, 계절 순이다. */
fun moodFor(weather: Weather, hour: Int, month: Int): SceneMood {
    val sunrise = SUNRISE_HOUR[month - 1]
    val sunset = SUNSET_HOUR[month - 1]
    return when {
        weather == Weather.RAIN -> SceneMood.RAIN
        weather == Weather.SNOW -> SceneMood.SNOW
        weather == Weather.FOG -> SceneMood.FOG
        weather == Weather.CLOUDY -> SceneMood.CLOUDY
        hour < sunrise || hour > sunset -> SceneMood.NIGHT
        hour <= sunrise + 1 -> SceneMood.SUNRISE
        hour >= sunset - 1 -> SceneMood.SUNSET
        month in 3..4 -> SceneMood.SPRING
        month in 6..8 -> SceneMood.SUMMER
        month in 10..11 -> SceneMood.AUTUMN
        month == 12 || month <= 2 -> SceneMood.WINTER
        else -> SceneMood.PLAIN
    }
}

/**
 * 분위기에 맞는 코스. 맞는 코스가 없으면 시간대로 고른다.
 * 눈과 안개는 길이 위험하므로 경치보다 안전을 먼저 본다. 산길을 빼고 오르막이 가장 적은 코스를 고른다.
 */
fun suggestFor(courses: List<Course>, mood: SceneMood, hour: Int): Course {
    if (mood == SceneMood.SNOW || mood == SceneMood.FOG) {
        val safe = courses.filter { "mountain" !in it.tags.themes }.ifEmpty { courses }
        return safe.minBy { factsOf(it).climbMeters }
    }
    val fitting = courses.filter { course ->
        val tags = course.tags
        when (mood) {
            SceneMood.RAIN -> "lake" in tags.themes
            SceneMood.CLOUDY -> "coast" in tags.themes
            SceneMood.SUNRISE -> "sunrise" in tags.times
            SceneMood.SUNSET -> "sunset" in tags.times
            SceneMood.NIGHT -> "night" in tags.times
            SceneMood.SPRING -> "spring" in tags.seasons
            SceneMood.SUMMER -> "summer" in tags.seasons
            SceneMood.AUTUMN -> "autumn" in tags.seasons
            SceneMood.WINTER -> "winter" in tags.seasons
            else -> false
        }
    }
    return if (fitting.isEmpty()) suggestFor(courses, hour) else fitting.maxBy { factsOf(it).curves }
}
