package com.byway.ui

import com.byway.core.course.AtlasSpot
import com.byway.core.course.Box
import com.byway.core.course.Px

private val BROWSE_FOCUS = mapOf(
    WindowClass.TWO to Box(196f, 40f, 744f, 1104f),
    WindowClass.FULL to Box(196f, 40f, 1324f, 1130f),
    WindowClass.THIRD to Box(24f, 116f, 793f, 440f),
)
private val MINE_FOCUS = mapOf(
    WindowClass.TWO to Box(816f, 96f, 844f, 960f),
    WindowClass.FULL to Box(1316f, 96f, 1204f, 990f),
    WindowClass.THIRD to Box(24f, 116f, 793f, 480f),
)

/** 지도가 들어갈 자리(dp). 글 칸과 메뉴를 뺀 곳이다. */
fun atlasFocus(tab: Tab, window: WindowClass): Box =
    (if (tab == Tab.MINE) MINE_FOCUS else BROWSE_FOCUS).getValue(window)

fun grow(box: Box, room: Float, least: Float): Box {
    val w = maxOf(box.w, least)
    val h = maxOf(box.h, least)
    val cx = box.x + box.w / 2
    val cy = box.y + box.h / 2
    return Box(cx - w * (0.5f + room), cy - h * (0.5f + room), w * (1 + 2 * room), h * (1 + 2 * room))
}

fun union(boxes: List<Box>): Box {
    val x = boxes.minOf { it.x }
    val y = boxes.minOf { it.y }
    return Box(x, y, boxes.maxOf { it.x + it.w } - x, boxes.maxOf { it.y + it.h } - y)
}

fun fit(box: Box, focus: Box): AtlasCamera =
    AtlasCamera(box.x + box.w / 2, box.y + box.h / 2, minOf(focus.w / box.w, focus.h / box.h))

/** 지도 좌표를 화면 좌표(dp)로 바꾼다. */
fun toScreen(x: Float, y: Float, camera: AtlasCamera, focus: Box): Px = Px(
    focus.x + focus.w / 2 + camera.scale * (x - camera.cx),
    focus.y + focus.h / 2 + camera.scale * (y - camera.cy),
)

fun courseCamera(spot: AtlasSpot, focus: Box): AtlasCamera = fit(grow(spot.box, 0.18f, 120f), focus)

/** 손을 뗐을 때 지금 자리를 저장할지. 핀을 눌러 확대가 시작됐으면 목표 자리가 이미 저장되어 있다. */
fun reportsCamera(lifted: Boolean, moving: Boolean): Boolean = lifted && !moving

fun clampScale(scale: Float, minScale: Float, maxScale: Float): Float = scale.coerceIn(minScale, maxScale)
