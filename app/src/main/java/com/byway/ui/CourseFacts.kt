package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.engine.DriveTuning
import com.byway.core.engine.planLegs
import com.byway.core.engine.planStops
import java.util.Locale

fun distanceText(course: Course): String {
    val kilometers = course.path.zipWithNext { a, b -> a.distanceTo(b) }.sum() / 1_000
    return String.format(Locale.ROOT, "%.1fkm", kilometers)
}

fun statsText(course: Course): String = "${distanceText(course)} · 구간 ${course.sections.size}개"

fun sectionsText(course: Course): String = course.sections.joinToString(" → ") { it.name }

/** 내비에 올라가는 지점 열. 코스 그림과 안내 문구가 같은 값을 쓴다. */
fun naviStops(course: Course) =
    planStops(course, course.toleranceMeters ?: DriveTuning().simplifyToleranceMeters)

fun naviPlanText(course: Course): String {
    val stops = naviStops(course)
    return "코스 좌표 ${course.path.size}개 → 내비 지점 ${stops.size}개 → ${planLegs(stops).size}번에 나눠 안내"
}
