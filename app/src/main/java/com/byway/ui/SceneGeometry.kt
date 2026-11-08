package com.byway.ui

/** 위성 장면 그림이 놓이는 자리(dp). 값은 시안 app.css 와 같다. */
data class SceneRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** 그림 안의 [from]에서 [to]까지 투명도가 바뀌는 띠. 가로는 [alphaFrom]에서 1로, 세로는 [alphaFrom]에서 0으로 간다. */
data class FadeBand(val vertical: Boolean, val from: Float, val to: Float, val alphaFrom: Float)

private const val SCENE_WIDTH = 2404f
private const val SCENE_HEIGHT = 1210f
private const val THIRD_WIDTH = 1683f
private const val THIRD_HEIGHT = 847f
private const val THIRD_TOP = 26f

fun sceneRect(window: WindowClass, viewW: Float, viewH: Float): SceneRect = when (window) {
    WindowClass.THIRD -> SceneRect(viewW - THIRD_WIDTH, THIRD_TOP, THIRD_WIDTH, THIRD_HEIGHT)
    else -> SceneRect(viewW - SCENE_WIDTH, viewH / 2 - SCENE_HEIGHT / 2, SCENE_WIDTH, SCENE_HEIGHT)
}

fun sceneFade(window: WindowClass): FadeBand = when (window) {
    WindowClass.TWO -> FadeBand(false, 1280f, 1680f, 0.2f)
    WindowClass.FULL -> FadeBand(false, 960f, 1420f, 0.2f)
    WindowClass.THIRD -> FadeBand(true, 590f, 706f, 1f)
}

/** 뒤에서 읽는 그림 중 가장 나중에 요청한 것만 쓴다. */
class LatestOnly {
    private var last = 0

    fun next(): Int = ++last

    fun isCurrent(token: Int): Boolean = token == last
}
