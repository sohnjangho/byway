package com.byway.core.course

import com.byway.core.geo.GeoPoint
import java.io.Serializable

/** 드라이빙 거리 구간(더 많은 코스 설계 5절). 경계값은 아래 구간에 넣는다. */
enum class LengthBand { UP_TO_30, UP_TO_60, UP_TO_100, OVER_100 }

fun bandOf(meters: Double): LengthBand = when {
    meters <= 30_000 -> LengthBand.UP_TO_30
    meters <= 60_000 -> LengthBand.UP_TO_60
    meters <= 100_000 -> LengthBand.UP_TO_100
    else -> LengthBand.OVER_100
}

/** 내 주변 범위. 차 위치에서 코스 출발 지점까지 직선거리다. */
enum class NearRange(val km: Int) { KM25(25), KM50(50), KM75(75) }

/**
 * 무드 키. "태그 종류:값"이다. 화면 순서대로 함께 가는 사람, 계절, 날씨, 시간대다.
 */
val MOOD_KEYS: List<String> = listOf(
    "moods:alone", "moods:date", "moods:family",
    "seasons:spring", "seasons:summer", "seasons:autumn", "seasons:winter",
    "weathers:rain",
    "times:sunrise", "times:day", "times:sunset", "times:night",
)

/** 더 많은 코스에서 고른 걸러 보기. 모든 조건을 함께(AND) 건다. */
data class CourseFilter(
    val query: String = "",
    val near: NearRange? = null,
    val mood: String? = null,
    val length: LengthBand? = null,
    val region: String? = null,
    val theme: String? = null,
) : Serializable

fun CourseFilter.isEmpty(): Boolean = this == CourseFilter(query = query) && query.isBlank()

/** 걸러진 코스. [awayMeters]는 차 위치를 알 때 출발 지점까지의 직선거리다. */
data class Found(val course: Course, val awayMeters: Double?)

/**
 * [metersOf]는 코스 거리, [wordsOf]는 검색에 함께 쓸 화면 문구(태그 이름 등)다.
 * 내 주변이 걸려 있어도 위치를 모르면 그 조건은 건너뛴다. 화면이 칩을 흐리게 두어 알린다.
 */
fun filterCourses(
    courses: List<Course>,
    filter: CourseFilter,
    here: GeoPoint?,
    metersOf: (Course) -> Double,
    wordsOf: (Course) -> List<String>,
): List<Found> {
    val words = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val found = courses.asSequence()
        .filter { filter.region == null || it.region == filter.region }
        .filter { filter.theme == null || filter.theme in it.tags.themes }
        .filter { filter.mood == null || hasMood(it.tags, filter.mood) }
        .filter { filter.length == null || bandOf(metersOf(it)) == filter.length }
        .filter { words.isEmpty() || searchText(it, wordsOf).let { text -> words.all(text::contains) } }
        .map { Found(it, here?.distanceTo(it.path.first())) }
        .toList()
    val near = filter.near ?: return found
    if (here == null) return found
    return found.filter { it.awayMeters!! <= near.km * 1_000.0 }.sortedBy { it.awayMeters }
}

private fun hasMood(tags: CourseTags, key: String): Boolean {
    val (kind, value) = key.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val values = when (kind) {
        "moods" -> tags.moods
        "seasons" -> tags.seasons
        "weathers" -> tags.weathers
        "times" -> tags.times
        else -> emptyList()
    }
    return value in values
}

private fun searchText(course: Course, wordsOf: (Course) -> List<String>): String =
    (listOf(course.name, course.region, course.summary) + course.spots.map { it.name } + course.sections.map { it.name } + wordsOf(course))
        .joinToString(" ").lowercase()
