package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.RankBy
import com.byway.service.DriveState
import java.io.Serializable

/** 레일 메뉴. */
enum class Tab(val title: String) {
    HOME("홈"),
    BROWSE("둘러보기"),
    RANKING("랭킹"),
    MINE("내 코스"),
}

/** 앱 창 크기. 전체 2560, 2/3 1700, 1/3 841 폭이다. */
enum class WindowClass { FULL, TWO, THIRD }

private const val THIRD_MAX_DP = 1200
private const val TWO_MAX_DP = 2100

fun windowClassOf(widthDp: Int): WindowClass = when {
    widthDp < THIRD_MAX_DP -> WindowClass.THIRD
    widthDp < TWO_MAX_DP -> WindowClass.TWO
    else -> WindowClass.FULL
}

/** 한국 지도를 보는 자리. 창이 바뀌어도 남도록 Nav 에 담는다. */
data class AtlasCamera(val cx: Float, val cy: Float, val scale: Float) : Serializable

/** 코스 상세 오른쪽 칸에 보여 줄 그림. */
enum class Picture(val title: String) {
    DRAWING("코스 그림"),
    STREET("지도"),
    SATELLITE("위성"),
    SCENERY("풍경"),
}

/** 풍경 사진이 있는 코스만 풍경을 고를 수 있다. */
fun picturesFor(hasScenery: Boolean): List<Picture> = Picture.entries.filter { it != Picture.SCENERY || hasScenery }

/** 운전자가 고른 것들. 창 막대를 끌어 Activity 가 다시 만들어져도 남도록 Bundle 에 담는다. */
data class Nav(
    val tab: Tab = Tab.HOME,
    /** 홈에 올린 코스. 없으면 지금 시간과 계절에 맞는 코스를 올린다. */
    val courseId: String? = null,
    val about: Boolean = false,
    val theme: String? = null,
    val region: String? = null,
    val rankBy: RankBy = RankBy.CURVES,
    /** 사용자가 움직인 한국 지도 자리. 없으면 화면이 정한다. */
    val atlas: AtlasCamera? = null,
    /** 둘러보기에서 고른 코스. 지도가 이 코스로 확대되어 있다. */
    val picked: String? = null,
) : Serializable

/** 한 Activity 안에서 바뀌는 화면들. */
sealed interface Screen {
    data class Browse(val tab: Tab) : Screen
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
    return Screen.Browse(nav.tab)
}

private const val WIDE_MIN_DP = 1200

/** 앱 칸 전체는 약 1682dp, 내비와 나누면 약 841dp 다. */
fun isWide(widthDp: Int): Boolean = widthDp >= WIDE_MIN_DP
