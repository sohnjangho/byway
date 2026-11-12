package com.byway.ui

/** 장면 그림이 놓이는 자리(dp). 값은 시안 app.css 와 같다. */
data class SceneRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** 그림 안의 [from]에서 [to]까지 투명도가 바뀌는 띠. 가로는 [alphaFrom]에서 1로, 세로는 [alphaFrom]에서 0으로 간다. */
data class FadeBand(val vertical: Boolean, val from: Float, val to: Float, val alphaFrom: Float)

private const val SCENE_WIDTH = 2404f
private const val SCENE_HEIGHT = 1210f
private const val THIRD_WIDTH = 1683f
private const val THIRD_HEIGHT = 847f
private const val THIRD_TOP = 26f
private const val STRIP_WIDTH = 1430f
private const val STRIP_HEIGHT = 720f
private const val LIST_WIDTH = 1394f
private const val LIST_HEIGHT = 702f
private const val LIST_TOP = 70f

/**
 * [list]는 랭킹처럼 1/3 창에서 목록 자리를 내느라 장면을 더 줄이는 화면이다.
 * [strip]은 1/3 창의 지도 아래쪽에 훑어보기 사진 띠를 얹는 화면이다. 경로가 띠 뒤로 들어가지 않게 줄인다.
 */
fun sceneRect(window: WindowClass, viewW: Float, viewH: Float, list: Boolean = false, strip: Boolean = false): SceneRect = when {
    window == WindowClass.THIRD && list -> SceneRect(viewW - LIST_WIDTH, LIST_TOP, LIST_WIDTH, LIST_HEIGHT)
    window == WindowClass.THIRD && strip -> SceneRect(viewW - STRIP_WIDTH, THIRD_TOP, STRIP_WIDTH, STRIP_HEIGHT)
    window == WindowClass.THIRD -> SceneRect(viewW - THIRD_WIDTH, THIRD_TOP, THIRD_WIDTH, THIRD_HEIGHT)
    else -> SceneRect(viewW - SCENE_WIDTH, viewH / 2 - SCENE_HEIGHT / 2, SCENE_WIDTH, SCENE_HEIGHT)
}

/** 글 칸 아래에 깔리는 지도의 불투명도. 글자 대비는 낮·밤 모두 4.5:1 이상이다(ThemeTest). */
const val MAP_UNDER_TEXT = 0.16f

/**
 * 지도가 글 칸과 만나는 띠. 넓은 창은 지도가 글 칸 아래 전체에 16%로 옅게 깔리고, 글자 오른쪽 끝(글 칸 끝 − SPACE_8)부터 짙어져
 * 칸 끝 50dp 뒤에서 다 드러난다(화면 다듬기 설계 1절). 글자 아래는 어디서도 16%를 넘지 않아 낮 위성 지도에서도 읽힌다.
 * 1/3 창은 지도가 아래로 길게 옅어져 글 칸과의 경계가 보이지 않는다. 값은 장면 그림 안의 좌표다.
 */
fun sceneFade(window: WindowClass, list: Boolean = false, strip: Boolean = false): FadeBand = when (window) {
    WindowClass.TWO -> FadeBand(false, 1448f, 1530f, MAP_UNDER_TEXT)
    WindowClass.FULL -> FadeBand(false, 1088f, 1170f, MAP_UNDER_TEXT)
    WindowClass.THIRD -> when {
        list -> FadeBand(true, 309f, 533f, 1f)
        strip -> FadeBand(true, 332f, 630f, 1f)
        else -> FadeBand(true, 423f, 711f, 1f)
    }
}

/** 지도가 글 칸과 만나는 띠를 화면 좌표로 옮긴 값. 주행 화면 배경은 이 띠에서 지도와 서로 넘어간다. */
fun sceneFadeOnScreen(window: WindowClass, viewW: Float, viewH: Float, strip: Boolean = false): FadeBand {
    val rect = sceneRect(window, viewW, viewH, strip = strip)
    val band = sceneFade(window, strip = strip)
    val start = if (band.vertical) rect.top else rect.left
    return band.copy(from = start + band.from, to = start + band.to)
}

/** 풍경 사진이 글 칸과 만나는 띠. 값은 화면 좌표다. */
fun photoFade(window: WindowClass, viewH: Float): FadeBand = when (window) {
    WindowClass.TWO -> FadeBand(false, 756f, 826f, 0f)
    WindowClass.FULL -> FadeBand(false, 1256f, 1336f, 0f)
    WindowClass.THIRD -> FadeBand(true, viewH * 0.5f, viewH * 0.9f, 1f)
}

/** 뒤에서 읽는 그림 중 가장 나중에 요청한 것만 쓴다. */
class LatestOnly {
    private var last = 0

    fun next(): Int = ++last

    fun isCurrent(token: Int): Boolean = token == last
}

/** 파티 확대 지도의 선과 표시에 거는 띠. 넓은 창은 지도와 같이 글 칸 아래에서 옅어지고, 1/3 칸은 글 칸과 겹치지 않아 띠가 없다. */
fun zoomMarksFade(window: WindowClass, viewW: Float, viewH: Float): FadeBand? =
    sceneFadeOnScreen(window, viewW, viewH).takeUnless { it.vertical }
