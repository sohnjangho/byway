package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseFilter
import com.byway.core.course.CourseTags
import com.byway.core.course.LengthBand
import com.byway.core.course.MOOD_KEYS
import com.byway.core.course.NearRange
import java.util.Locale

/** 더 많은 코스의 지역 칩. 실제 코스와 더미 코스가 같은 이름을 쓴다. */
internal val REGIONS = listOf("수도권", "강원", "충청", "경상", "전라", "남해안", "제주")

/** 풍경 칩. 지금 둘러보기 칩과 같은 다섯 가지다. */
internal val SCENERY = listOf("coast" to "바다", "mountain" to "고개", "river" to "강", "lake" to "호수", "forest" to "숲")

/** 걸러 보기 버튼의 종류. 화면 왼쪽부터 이 순서다. */
internal val FILTER_KINDS = listOf("near", "mood", "length", "region", "theme")

private val FILTER_NAMES = mapOf("near" to "내 주변", "mood" to "무드", "length" to "거리", "region" to "지역", "theme" to "풍경")

private val MOOD_WORDS = mapOf(
    "moods:alone" to "혼자", "moods:date" to "데이트", "moods:family" to "가족",
    "seasons:spring" to "봄", "seasons:summer" to "여름", "seasons:autumn" to "가을", "seasons:winter" to "겨울",
    "weathers:rain" to "비 오는 날",
    "times:sunrise" to "일출", "times:day" to "낮", "times:sunset" to "노을", "times:night" to "밤",
)

const val SEARCH_HINT = "코스 이름, 지역, 장소로 찾기"
const val CLEAR_FILTERS = "모두 풀기"
const val NO_POSITION = "현재 위치를 아직 모릅니다"
const val NOTHING_FOUND = "맞는 코스가 없습니다"

fun moodLabel(key: String): String = MOOD_WORDS[key] ?: key

fun lengthLabel(band: LengthBand): String = when (band) {
    LengthBand.UP_TO_30 -> "30km 이하"
    LengthBand.UP_TO_60 -> "30~60km"
    LengthBand.UP_TO_100 -> "60~100km"
    LengthBand.OVER_100 -> "100km 넘게"
}

fun nearLabel(range: NearRange): String = "${range.km}km"

/** 걸러 보기 버튼 글. 고른 값이 없으면 버튼 이름, 있으면 그 값이다. */
fun filterButtonText(kind: String, filter: CourseFilter): String = when (kind) {
    "near" -> filter.near?.let { "${nearLabel(it)} 안" }
    "mood" -> filter.mood?.let(::moodLabel)
    "length" -> filter.length?.let(::lengthLabel)
    "region" -> filter.region
    "theme" -> filter.theme?.let { key -> SCENERY.firstOrNull { it.first == key }?.second ?: key }
    else -> null
} ?: FILTER_NAMES.getValue(kind)

/** 내 주변을 걸었거나 그 줄을 펼쳤는데 위치를 모르면 이유를 보인다. 그동안 내 주변 조건은 걸리지 않는다. */
fun nearNote(filter: CourseFilter, open: String?, located: Boolean): String? =
    NO_POSITION.takeIf { !located && (filter.near != null || open == "near") }

fun awayText(meters: Double): String = String.format(Locale.ROOT, "%dkm 거리", maxOf(1, Math.round(meters / 1_000).toInt()))

/** 검색어를 맞춰 볼 화면 문구: 태그 이름, 무드 이름, 더미 표시. 더 많은 코스와 Gleo 음성 찾기가 같이 쓴다. */
fun searchWords(course: Course): List<String> = tagLabels(course.tags) + moodWords(course.tags) + listOfNotNull(DUMMY_WORD.takeIf { course.dummy })

/** 검색에 함께 쓰는 무드 이름. "비 오는 날"로 찾으면 비 오는 날 코스가 나온다. */
fun moodWords(tags: CourseTags): List<String> = MOOD_KEYS.filter { key ->
    val (kind, value) = key.split(":")
    value in when (kind) {
        "moods" -> tags.moods
        "seasons" -> tags.seasons
        "weathers" -> tags.weathers
        else -> tags.times
    }
}.map(::moodLabel)

/** 목록 한 줄의 둘째 줄. 더미 표시, 내 주변 거리(켰을 때), 지역 · 거리 · 추천 순서다. */
fun rowLine(course: com.byway.core.course.Course, meters: Double, away: Double?, showAway: Boolean): String {
    val near = away?.takeIf { showAway }?.let { "${awayText(it)} · " }.orEmpty()
    return withDummyMark(course, near + courseLine(course.region, meters, pickLabels(course.tags).firstOrNull().orEmpty()))
}
