package com.byway.ui

import com.byway.core.course.CourseTags

private val THEMES = mapOf(
    "river" to "강변",
    "forest" to "숲길",
    "mountain" to "산길",
    "lake" to "호수",
    "city" to "도심",
    "coast" to "해안",
)
private val TIMES = mapOf("sunset" to "노을", "sunrise" to "일출", "day" to "낮", "night" to "밤")
private val MOODS = mapOf("alone" to "혼자", "date" to "데이트", "family" to "가족")
private val SEASONS = mapOf("spring" to "봄에", "summer" to "여름에", "autumn" to "가을에", "winter" to "겨울에")
private val AGES = mapOf("20s" to "20대", "30s" to "30대", "40s" to "40대", "50s" to "50대")

/** 추천 표시의 낱말. 편집 추천이고 주행 통계가 아니어서 순위를 뜻하는 말을 쓰지 않는다. */
private const val PICK_WORD = "추천"
private val SPOT_KINDS = mapOf("view" to "전망", "food" to "식사", "rest" to "쉼터", "photo" to "사진")

/** 코스 태그를 화면 문구로 바꾼다. 표에 없는 값은 표시하지 않는다. */
fun tagLabels(tags: CourseTags): List<String> =
    tags.themes.mapNotNull(THEMES::get) + tags.times.mapNotNull(TIMES::get) + tags.moods.mapNotNull(MOODS::get)

/** 코스를 고를 이유로 보이는 표시. 계절, 나이대, 분위기에서 대표값 하나씩 쓴다. */
fun pickLabels(tags: CourseTags): List<String> = listOfNotNull(
    tags.seasons.firstOrNull()?.let(SEASONS::get)?.let { "$it $PICK_WORD" },
    tags.ages.firstOrNull()?.let(AGES::get)?.let { "$it $PICK_WORD" },
    tags.moods.firstOrNull()?.let(MOODS::get),
)

fun themeLabel(key: String): String? = THEMES[key]

/** 테마 타일이 쓸 구간 색의 분위기. */
fun themeMood(theme: String): String = when (theme) {
    "river", "lake", "coast" -> "river"
    "forest", "mountain" -> "forest"
    else -> "city"
}

fun spotKindLabel(kind: String): String? = SPOT_KINDS[kind]
