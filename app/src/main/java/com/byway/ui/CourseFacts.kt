package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseFacts
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked
import com.byway.core.course.Stop
import com.byway.core.engine.DriveTuning
import com.byway.core.engine.planLegs
import com.byway.core.engine.planStops
import java.util.Locale

private const val NO_VALUE = "-"

fun kmText(meters: Double): String = String.format(Locale.ROOT, "%.1fkm", meters / 1_000)

fun ratioText(facts: CourseFacts): String = String.format(Locale.ROOT, "%.1f배", facts.eumRatio)

/** 고도 자료가 없는 코스는 0m 로 쓰지 않는다. 평지처럼 보이면 거짓 수치가 된다. */
fun climbText(course: Course, facts: CourseFacts): String =
    if (course.elevation.isEmpty()) NO_VALUE else "${facts.climbMeters}m"

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
    RankBy.EUM -> ratioText(ranked.facts)
    RankBy.CLIMB -> climbText(ranked.course, ranked.facts)
    RankBy.LENGTH -> kmText(ranked.facts.meters)
}

fun rankTitle(by: RankBy): String = when (by) {
    RankBy.CURVES -> "굽이"
    RankBy.EUM -> "에움 배율"
    RankBy.CLIMB -> "오르막"
    RankBy.LENGTH -> "거리"
}

fun rankNote(by: RankBy): String = when (by) {
    RankBy.CURVES -> "인기 순위가 아니라 코스 선에서 계산한 굽이 수입니다"
    RankBy.EUM -> "인기 순위가 아니라 출발과 도착의 직선거리보다 몇 배 돌아가는지 계산한 값입니다"
    RankBy.CLIMB -> "인기 순위가 아니라 코스 전체의 누적 오르막을 계산한 값입니다"
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
    "코스 좌표 ${String.format(Locale.ROOT, "%,d", course.path.size)}개 → 내비 지점 ${stops.size}개 → ${planLegs(stops).size}번에 나눠 안내"
