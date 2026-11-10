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

/** 화면을 나눈 뒤 이어 할 출발. 3분할 안내 중에 [Nav]에 담는다. */
data class StartRequest(val courseId: String, val resumeFrom: Int) : Serializable

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
    /** 홈 코스 띠를 넘겨 둔 자리(px). */
    val stripX: Int? = null,
    val settings: Boolean = false,
    /** 내 코스에서 "길 기록하기"를 눌러 기록 동의를 묻는 중. */
    val recordConsent: Boolean = false,
    /** 공유 범위를 고르는 내 코스 id. */
    val share: String? = null,
    /** 드라이빙 파티 화면(만들기·참여·멤버·위치 공유). */
    val party: Boolean = false,
    /** 3분할 설정에서 화면을 나누라고 안내하는 중인 출발. 나뉘면 이 출발을 시작한다. */
    val splitGuide: StartRequest? = null,
) : Serializable

/** 메뉴를 누르면 설정과 앱 정보가 닫히고 지도에서 고른 것이 풀린다. */
fun Nav.toTab(tab: Tab): Nav = copy(tab = tab, about = false, settings = false, picked = null, atlas = null, recordConsent = false, share = null, party = false, splitGuide = null)

/** 주행이 시작되면 정차 전용 화면을 닫아 둔다. 다시 정차했을 때 메뉴 화면으로 돌아온다. */
fun Nav.whileDriving(): Nav = copy(about = false, settings = false, recordConsent = false, share = null, party = false, splitGuide = null)

/** 메뉴에서 표시할 항목. 설정과 앱 정보가 열려 있으면 메뉴 대신 설정에 표시한다. */
fun markedTab(nav: Nav): Tab? = nav.tab.takeUnless { nav.settings || nav.about }

/** 메뉴 안의 위에서부터 자리. 설정이 맨 아래다. 내용이 들어오는 방향을 정할 때 쓴다. */
fun railPosition(nav: Nav): Int = markedTab(nav)?.ordinal ?: Tab.entries.size

/** 한 Activity 안에서 바뀌는 화면들. */
sealed interface Screen {
    data class Browse(val tab: Tab) : Screen
    data object Drive : Screen
    data class Summary(val course: Course) : Screen
    data object Waiting : Screen
    data object About : Screen
    data object Settings : Screen

    /** 달리다 만 코스를 이어갈지 묻는다. */
    data object Resume : Screen

    /** 달린 길 기록. 동의(정차), 기록 중(주행·정차), 저장(정차). */
    data object RecordConsent : Screen
    data object Recording : Screen
    data object SaveRecord : Screen

    /** 내가 만든 코스의 공유 범위 고르기와 동의. 정차 중에만. */
    data class Share(val courseId: String) : Screen

    /** 드라이빙 파티. 정차 중에만. */
    data object Party : Screen

    /** 3분할로 출발하려면 화면을 나누라는 안내. 정차 중에만. */
    data object SplitGuide : Screen
}

/**
 * 어떤 화면을 보여 줄지 정하는 유일한 곳. 주행 규제가 여기에 걸려 있다.
 * P 단이 아니면 주행 화면과 대기 화면만 나온다. 탭과 정차 화면은 그 아래에서만 고른다.
 * [resumable]은 달리다 만 코스가 저장되어 있다는 뜻이다. 정차 화면보다 먼저 이어갈지 묻는다.
 */
fun screenFor(state: DriveState, nav: Nav, resumable: Boolean = false): Screen {
    if (state.running) return Screen.Drive
    if (state.recording != null) return Screen.Recording
    if (!state.parked) return Screen.Waiting
    if (state.recorded != null) return Screen.SaveRecord
    state.finished?.let { return Screen.Summary(it) }
    if (nav.splitGuide != null) return Screen.SplitGuide
    if (resumable) return Screen.Resume
    if (nav.recordConsent) return Screen.RecordConsent
    nav.share?.let { return Screen.Share(it) }
    if (nav.party) return Screen.Party
    if (nav.about) return Screen.About
    if (nav.settings) return Screen.Settings
    return Screen.Browse(nav.tab)
}

private const val WIDE_MIN_DP = 1200

/** 앱 칸 전체는 약 1682dp, 내비와 나누면 약 841dp 다. */
fun isWide(widthDp: Int): Boolean = widthDp >= WIDE_MIN_DP
