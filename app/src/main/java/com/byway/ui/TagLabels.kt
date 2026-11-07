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
private val SPOT_KINDS = mapOf("view" to "전망", "food" to "식사", "rest" to "쉼터", "photo" to "사진")

/** 코스 태그를 화면 문구로 바꾼다. 표에 없는 값은 표시하지 않는다. */
fun tagLabels(tags: CourseTags): List<String> =
    tags.themes.mapNotNull(THEMES::get) + tags.times.mapNotNull(TIMES::get) + tags.moods.mapNotNull(MOODS::get)

fun themeLabel(key: String): String? = THEMES[key]

/** 테마 타일이 쓸 구간 색의 분위기. */
fun themeMood(theme: String): String = when (theme) {
    "river", "lake", "coast" -> "river"
    "forest", "mountain" -> "forest"
    else -> "city"
}

fun spotKindLabel(kind: String): String? = SPOT_KINDS[kind]
