package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.RankBy
import com.byway.service.DriveState
import java.io.Serializable

/** 상단에 고정되는 메뉴. */
enum class Tab(val title: String) {
    HOME("홈"),
    THEME("테마"),
    RANKING("랭킹"),
    REGION("지역"),
    MINE("내 코스"),
}

/** 코스 상세 오른쪽 칸에 보여 줄 그림. */
enum class Picture(val title: String) {
    DRAWING("코스 그림"),
    STREET("지도"),
    SATELLITE("위성"),
}

/** 운전자가 고른 것들. 창 막대를 끌어 Activity 가 다시 만들어져도 남도록 Bundle 에 담는다. */
data class Nav(
    val tab: Tab = Tab.HOME,
    val courseId: String? = null,
    val about: Boolean = false,
    val theme: String? = null,
    val region: String? = null,
    val rankBy: RankBy = RankBy.CURVES,
    val picture: Picture = Picture.SATELLITE,
) : Serializable

/** 한 Activity 안에서 바뀌는 화면들. */
sealed interface Screen {
    data class Browse(val tab: Tab) : Screen
    data class Preview(val courseId: String) : Screen
    data object Drive : Screen
    data class Summary(val course: Course) : Screen
    data object Waiting : Screen
    data object About : Screen
}

/**
 * 어떤 화면을 보여 줄지 정하는 유일한 곳. 주행 규제가 여기에 걸려 있다.
 * P 단이 아니면 주행 화면과 대기 화면만 나온다. 탭과 정차 화면은 그 아래에서만 고른다.
 */
fun screenFor(state: DriveState, nav: Nav): Screen {
    if (state.running) return Screen.Drive
    if (!state.parked) return Screen.Waiting
    state.finished?.let { return Screen.Summary(it) }
    if (nav.about) return Screen.About
    return nav.courseId?.let(Screen::Preview) ?: Screen.Browse(nav.tab)
}

private const val WIDE_MIN_DP = 1200

/** 앱 칸 전체는 약 1682dp, 내비와 나누면 약 841dp 다. */
fun isWide(widthDp: Int): Boolean = widthDp >= WIDE_MIN_DP
