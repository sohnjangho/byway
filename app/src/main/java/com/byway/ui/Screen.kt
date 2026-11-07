package com.byway.ui

import com.byway.core.course.Course
import com.byway.service.DriveState

/** 한 Activity 안에서 바뀌는 화면들. */
sealed interface Screen {
    data object Home : Screen
    data class Preview(val courseId: String) : Screen
    data object Drive : Screen
    data class Summary(val course: Course) : Screen
    data object Waiting : Screen
    data object About : Screen
}

/**
 * 어떤 화면을 보여 줄지 정하는 유일한 곳. 주행 규제가 여기에 걸려 있다.
 * P 단이 아니면 주행 화면과 대기 화면만 나온다. 정차 화면은 그 아래에서만 고른다.
 */
fun screenFor(state: DriveState, selectedCourseId: String?, showAbout: Boolean): Screen {
    if (state.running) return Screen.Drive
    if (!state.parked) return Screen.Waiting
    state.finished?.let { return Screen.Summary(it) }
    if (showAbout) return Screen.About
    return selectedCourseId?.let(Screen::Preview) ?: Screen.Home
}
