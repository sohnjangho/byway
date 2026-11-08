package com.byway.ui

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** Pleos 디자인 가이드 Interaction 의 값. 정차 화면에서만 쓴다. */
object MotionSpec {
    const val SHORT1 = 100L
    const val SHORT2 = 150L
    const val MEDIUM1 = 250L
    const val MEDIUM2 = 300L
    const val LONG1 = 400L
    const val LONG2 = 450L
    val DURATIONS = listOf(SHORT1, SHORT2, MEDIUM1, MEDIUM2, LONG1, LONG2)
    const val PRESS_SCALE = 0.95f

    /** 목록 끝에서 당겨지는 최대 거리는 스크롤 영역의 약 2/5 다. */
    const val BOUNCE_RANGE = 0.4f
}

private const val STAGGER_STEP = 30L
private const val PIN_START = 0.6f
private const val RUBBER = 0.55f

/** 화면을 바꿀 때의 움직임. */
enum class ScreenMotion { NONE, FADE, SLIDE, COURSE }

/** 주행 화면으로 들어가거나 나올 때와 창·테마가 바뀔 때는 움직이지 않는다. */
fun screenMotion(before: Screen?, after: Screen, beforeCourse: String?, afterCourse: String?, sameFrame: Boolean): ScreenMotion = when {
    before == null || !sameFrame || before.isDriving() || after.isDriving() -> ScreenMotion.NONE
    before != after -> ScreenMotion.SLIDE
    after == Screen.Browse(Tab.HOME) && beforeCourse != afterCourse -> ScreenMotion.COURSE
    else -> ScreenMotion.FADE
}

private fun Screen.isDriving() = this == Screen.Drive || this == Screen.Waiting

/** 메뉴가 아래로 가면 내용은 아래에서 들어온다. */
fun enterSign(before: Tab, after: Tab): Int = after.ordinal.compareTo(before.ordinal)

fun staggerDelay(index: Int): Long = min(index * STAGGER_STEP, MotionSpec.LONG2 - MotionSpec.MEDIUM1)

/** 구간 선을 앞에서부터 이어서 그릴 때 선마다 보이는 길이. */
fun revealedLengths(lengths: List<Float>, reveal: Float): List<Float> {
    var left = lengths.sum() * reveal
    return lengths.map { length ->
        val shown = min(length, left)
        left -= shown
        shown
    }
}

fun pinAlpha(reveal: Float): Float = ((reveal - PIN_START) / (1f - PIN_START)).coerceIn(0f, 1f)

/** 당긴 거리에 저항을 준다. 처음에는 절반쯤 따라오고 아무리 당겨도 [range]를 넘지 않는다. */
fun rubberBand(pulled: Float, range: Float): Float =
    sign(pulled) * (1f - 1f / (abs(pulled) * RUBBER / range + 1f)) * range

/** 손가락이 [delta]만큼 움직인 뒤 목록 끝을 넘어 당긴 양. 양수는 앞쪽 끝이다. 내용이 칸에 다 들어가면 당기지 않는다. */
fun pullAfter(pulled: Float, delta: Float, canScrollBack: Boolean, canScrollForward: Boolean): Float {
    if (pulled != 0f) {
        val next = pulled + delta
        return if (next * pulled <= 0f) 0f else next
    }
    return when {
        !canScrollBack && !canScrollForward -> 0f
        delta > 0f && !canScrollBack -> delta
        delta < 0f && !canScrollForward -> delta
        else -> 0f
    }
}

/** 풍경 사진이 얼마나 보이는지(0~1). 어느 쪽으로 밀어도 같다. */
fun swipeProgress(showingPhoto: Boolean, drag: Float, distance: Float): Float {
    val moved = min(abs(drag) / distance, 1f)
    return if (showingPhoto) 1f - moved else moved
}

/** 손을 뗐을 때 사진으로 갈지. 빠르게 튕기면 그 방향을, 아니면 가까운 쪽을 따른다. */
fun settlesOnPhoto(progress: Float, towardPhoto: Float, flingSpeed: Float): Boolean =
    if (abs(towardPhoto) >= flingSpeed) towardPhoto > 0f else progress >= 0.5f

/** 메뉴의 선택 표시 자리. */
data class MarkBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toward(target: MarkBox, t: Float) = MarkBox(
        left + (target.left - left) * t,
        top + (target.top - top) * t,
        right + (target.right - right) * t,
        bottom + (target.bottom - bottom) * t,
    )
}
