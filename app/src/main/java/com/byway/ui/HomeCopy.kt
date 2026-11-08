package com.byway.ui

import com.byway.core.course.SceneMood
import com.byway.core.course.ScenePlace

/** 홈 첫 줄. 시안 Main 의 문구와 같다. */
fun kickFor(mood: SceneMood): String = when (mood) {
    SceneMood.PLAIN -> "오늘 달리기 좋은 길"
    SceneMood.RAIN -> "비 오는 날 호숫가로 가는 길"
    SceneMood.SNOW -> "눈 오는 날, 오르막이 가장 적은 길"
    SceneMood.FOG -> "안개 낀 날, 오르막이 가장 적은 길"
    SceneMood.CLOUDY -> "구름 낀 날 바다를 끼고 달리는 길"
    SceneMood.SUNRISE -> "해 뜨는 시간에 어울리는 길"
    SceneMood.SUNSET -> "노을이 지는 시간에 어울리는 길"
    SceneMood.NIGHT -> "밤에 달리기 좋은 길"
    SceneMood.SPRING -> "봄에 달리기 좋은 길"
    SceneMood.SUMMER -> "여름에 달리기 좋은 길"
    SceneMood.AUTUMN -> "단풍철에 달리기 좋은 길"
    SceneMood.WINTER -> "겨울에 달리기 좋은 길"
}

private const val PER_KIND = 2

/** 코스 주변 목록. 경치, 명소, 음식을 종류마다 둘까지 두고 번호를 다시 매긴다. */
fun shownPlaces(places: List<ScenePlace>): List<ScenePlace> {
    val used = mutableMapOf<String, Int>()
    return places.filter { place ->
        val kind = if (place.kind == "photo") "view" else place.kind
        val count = used.getOrDefault(kind, 0)
        used[kind] = count + 1
        count < PER_KIND
    }.mapIndexed { i, place -> place.copy(no = i + 1) }
}

fun placeLine(place: ScenePlace): String {
    val km = if (place.km % 1.0 == 0.0) "${place.km.toInt()}" else "${place.km}"
    return listOf(place.label, place.note, "${km}km 지점").filter { it.isNotBlank() }.joinToString(" · ")
}

/** 출처가 없는 사진은 보이지 않는다. */
fun canSwipeToScenery(credit: String, hasFile: Boolean): Boolean = credit.isNotBlank() && hasFile
