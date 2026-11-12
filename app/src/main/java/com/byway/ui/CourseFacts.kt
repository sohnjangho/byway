package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseFacts
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked
import com.byway.core.course.Stop
import com.byway.core.course.funScore
import com.byway.core.course.naturePercent
import com.byway.core.engine.DriveTuning
import com.byway.core.engine.routeUploads
import com.byway.core.engine.planStops
import java.util.Locale

private const val NO_VALUE = "-"

fun kmText(meters: Double): String = String.format(Locale.ROOT, "%.1fkm", meters / 1_000)

/** 코스 카드의 둘째 줄. 지역, 거리, 대표 추천을 잇고 없는 것은 뺀다. */
fun courseLine(region: String, meters: Double, pick: String): String =
    listOf(region, kmText(meters), pick).filter { it.isNotBlank() }.joinToString(" · ")

fun topText(course: Course, facts: CourseFacts): String =
    if (course.elevation.isEmpty()) NO_VALUE else "${facts.topMeters}m"

/** 값 글자에서 단위가 시작하는 자리. 숫자는 크게, 단위는 작게 쓸 때 쓴다. */
fun unitStart(text: String): Int {
    val end = text.indexOfLast { it.isDigit() } + 1
    return if (end == 0) text.length else end
}

/** 구간 분위기를 길의 종류로 읽는다. */
fun moodWord(mood: String): String = when (mood) {
    "forest" -> "숲길"
    "river" -> "강변"
    else -> "도심"
}

fun rankValue(by: RankBy, ranked: Ranked): String = when (by) {
    RankBy.CURVES -> "${ranked.facts.curves}개"
    RankBy.FUN -> "${funScore(ranked.facts, ranked.course.elevation.isNotEmpty())}점"
    RankBy.SCENERY -> "${naturePercent(ranked.facts.moodShare)}%"
    RankBy.LENGTH -> kmText(ranked.facts.meters)
}

fun rankTitle(by: RankBy): String = when (by) {
    RankBy.CURVES -> "커브"
    RankBy.FUN -> "운전 즐거움"
    RankBy.SCENERY -> "경치"
    RankBy.LENGTH -> "거리"
}

fun rankNote(by: RankBy): String = when (by) {
    RankBy.CURVES -> "인기 순위가 아니라 코스 선에서 계산한 커브 수입니다"
    RankBy.FUN -> "인기 순위가 아니라 커브, 고도 변화, 돌아가는 정도, 도심 비율로 계산한 점수입니다"
    RankBy.SCENERY -> "인기 순위가 아니라 도심 밖을 달리는 거리의 비율입니다"
    RankBy.LENGTH -> "인기 순위가 아니라 코스 전체 길이를 계산한 값입니다"
}

/** 시간대 나눔은 core 의 suggestFor 와 같다. */
fun suggestionLine(hour: Int): String {
    val word = when (hour) {
        in 5..8 -> "아침"
        in 9..15 -> "낮"
        in 16..19 -> "저녁"
        else -> "밤"
    }
    return "오늘 $word 달리기 좋은 길"
}

/** 내비에 올라가는 지점 열. 코스 그림과 안내 문구가 같은 값을 쓴다. */
fun naviStops(course: Course) =
    planStops(course, course.toleranceMeters ?: DriveTuning().simplifyToleranceMeters)

fun naviPlanText(course: Course, stops: List<Stop> = naviStops(course)): String =
    "코스 좌표 ${String.format(Locale.ROOT, "%,d", course.path.size)}개 → 내비 지점 ${stops.size}개 → ${routeUploads(stops.size)}번에 나눠 안내"
