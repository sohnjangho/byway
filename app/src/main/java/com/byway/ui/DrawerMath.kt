package com.byway.ui

/** 서랍이 나오는 쪽. */
enum class DrawerEdge { LEFT, BOTTOM }

/** 넓은 창은 운전석 쪽 글 칸 위로 나온다. 1/3 창은 글 칸이 지도 아래에 있어 아래에서 올라온다. */
fun drawerEdge(window: WindowClass): DrawerEdge = if (window == WindowClass.THIRD) DrawerEdge.BOTTOM else DrawerEdge.LEFT

/** 지금 고른 것을 맨 위에 두고 나머지는 차례를 지킨다. */
fun <T> drawerOrder(items: List<T>, chosenId: String, idOf: (T) -> String): List<T> =
    items.filter { idOf(it) == chosenId } + items.filter { idOf(it) != chosenId }

/** 서랍이 [shown](0~1)만큼 나왔을 때 제자리에서 밀려나 있는 거리(가로, 세로). */
fun drawerShift(edge: DrawerEdge, shown: Float, width: Float, height: Float): Pair<Float, Float> = when (edge) {
    DrawerEdge.LEFT -> width * (shown - 1f) to 0f
    DrawerEdge.BOTTOM -> 0f to height * (1f - shown)
}
