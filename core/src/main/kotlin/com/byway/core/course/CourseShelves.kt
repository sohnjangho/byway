package com.byway.core.course

import kotlin.math.roundToInt

/** 랭킹 기준. 인기 순위는 서버가 생긴 뒤에 더한다. 지금은 코스에서 계산한 값만 쓴다. */
enum class RankBy { CURVES, FUN, SCENERY, LENGTH }

data class Ranked(val course: Course, val facts: CourseFacts)

data class Shelf(val key: String, val count: Int)

private val MOOD_NAMES = mapOf("forest" to "숲", "river" to "물가", "city" to "도심")

fun rank(courses: List<Course>, by: RankBy): List<Ranked> =
    courses.map { Ranked(it, factsOf(it)) }.sortedByDescending { value(it, by) }

private fun value(ranked: Ranked, by: RankBy): Double = when (by) {
    RankBy.CURVES -> ranked.facts.curves.toDouble()
    RankBy.FUN -> funScore(ranked.facts, ranked.course.elevation.isNotEmpty()).toDouble()
    // 도심 밖 비율이 같으면 전망 명소가 많은 코스가 앞선다.
    RankBy.SCENERY -> naturePercent(ranked.facts.moodShare) + viewCount(ranked.course) / 100.0
    RankBy.LENGTH -> ranked.facts.meters
}

fun withTheme(courses: List<Course>, theme: String): List<Course> = courses.filter { theme in it.tags.themes }

fun inRegion(courses: List<Course>, region: String): List<Course> = courses.filter { it.region == region }

fun themes(courses: List<Course>): List<Shelf> = counted(courses.flatMap { it.tags.themes })

fun regions(courses: List<Course>): List<Shelf> = counted(courses.map { it.region }.filter { it.isNotEmpty() })

private fun counted(keys: List<String>): List<Shelf> =
    keys.groupingBy { it }.eachCount().map { Shelf(it.key, it.value) }.sortedByDescending { it.count }

/** 지금 시각에 어울리는 코스. 맞는 코스가 없으면 가장 굽이진 코스를 고른다. */
fun suggestFor(courses: List<Course>, hour: Int): Course {
    val time = when (hour) {
        in 5..8 -> "sunrise"
        in 16..19 -> "sunset"
        in 9..15 -> "day"
        else -> "night"
    }
    val fitting = courses.filter { time in it.tags.times }.ifEmpty { courses }
    return fitting.maxBy { factsOf(it).curves }
}

/** 코스가 다른 코스와 가장 다른 점을 한 줄로 쓴다. 전체에서 혼자 앞선 수치가 있으면 그것을 쓴다. */
fun characterOf(course: Course, all: List<Course>): String {
    val mine = factsOf(course)
    val others = all.filter { it.id != course.id }.map(::factsOf)
    fun leads(pick: (CourseFacts) -> Double) = pick(mine) > 0 && others.all { pick(it) < pick(mine) }
    return when {
        leads { it.curves.toDouble() } -> "커브가 가장 많은 길"
        leads { shownRatio(it) } -> "가장 많이 돌아가는 길"
        leads { it.meters } -> "가장 긴 길 %.1fkm".format(java.util.Locale.ROOT, mine.meters / 1_000)
        leads { it.climbMeters.toDouble() } -> "가장 높이 오르는 길"
        else -> largestScenery(mine)
    }
}

/** 화면에 보이는 소수 한 자리로 비교한다. 곧은길끼리의 미세한 차이를 특색으로 치지 않는다. */
private fun shownRatio(facts: CourseFacts) = (facts.eumRatio * 10).roundToInt() / 10.0 - 1

private fun largestScenery(facts: CourseFacts): String {
    val scenery = facts.moodShare.filterKeys { it != "city" }.ifEmpty { facts.moodShare }
    val top = scenery.maxByOrNull { it.value } ?: return ""
    return "${MOOD_NAMES[top.key] ?: top.key}${if (top.key == "forest") "이" else "가"} ${top.value}%인 길"
}
