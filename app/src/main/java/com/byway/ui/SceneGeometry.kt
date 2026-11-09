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
private const val STRIP_WIDTH = 1250f
private const val STRIP_HEIGHT = 629f
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

/**
 * 지도가 글 칸과 만나는 띠. 넓은 창은 글 칸이 바탕색이고 지도가 그 옆에서 짧게 시작한다.
 * 1/3 창은 지도가 아래로 길게 옅어져 글 칸과의 경계가 보이지 않는다. 값은 장면 그림 안의 좌표다.
 */
fun sceneFade(window: WindowClass, list: Boolean = false, strip: Boolean = false): FadeBand = when (window) {
    WindowClass.TWO -> FadeBand(false, 1460f, 1530f, 0f)
    WindowClass.FULL -> FadeBand(false, 1140f, 1220f, 0f)
    WindowClass.THIRD -> when {
        list -> FadeBand(true, 309f, 533f, 1f)
        strip -> FadeBand(true, 290f, 550f, 1f)
        else -> FadeBand(true, 423f, 711f, 1f)
    }
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
