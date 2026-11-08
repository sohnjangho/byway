package com.byway.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/** Pleos 디자인 가이드의 감속 곡선. */
internal val motionEase = PathInterpolator(0.16f, 1f, 0.3f, 1f)

/** 누르는 동안 95%로 줄어든다. 손가락이 칸 밖으로 나가면 눌림이 풀려 함께 돌아온다. */
internal fun <T : View> T.pressMotion(): T = apply {
    stateListAnimator = StateListAnimator().apply {
        addState(intArrayOf(android.R.attr.state_pressed), scaleTo(this@pressMotion, MotionSpec.PRESS_SCALE, MotionSpec.SHORT1))
        addState(intArrayOf(), scaleTo(this@pressMotion, 1f, MotionSpec.SHORT2))
    }
}

private fun scaleTo(view: View, scale: Float, millis: Long): Animator = AnimatorSet().apply {
    playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, scale), ObjectAnimator.ofFloat(view, View.SCALE_Y, scale))
    duration = millis
    interpolator = motionEase
}

/** 글줄이 차례로 올라오며 나타난다. */
internal fun staggerIn(views: List<View>, rise: Float) = views.forEachIndexed { i, view ->
    view.alpha = 0f
    view.translationY = rise
    view.animate().alpha(1f).translationY(0f)
        .setStartDelay(staggerDelay(i))
        .setDuration(MotionSpec.MEDIUM1)
        .setInterpolator(motionEase)
}

/** ScrollView 와 HorizontalScrollView 의 끝에서 당기면 저항을 받고, 놓으면 제자리로 돌아온다. */
@SuppressLint("ClickableViewAccessibility")
internal fun <T : FrameLayout> T.bounceAtEnds(vertical: Boolean): T = apply {
    overScrollMode = View.OVER_SCROLL_NEVER
    var last = Float.NaN
    var pulled = 0f
    setOnTouchListener { _, event ->
        val child = getChildAt(0) ?: return@setOnTouchListener false
        val at = if (vertical) event.y else event.x
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> last = at
            MotionEvent.ACTION_MOVE -> {
                // 눌린 곳이 카드면 DOWN 은 카드가 받아서 여기로 오지 않는다.
                if (last.isNaN()) last = at
                val delta = at - last
                last = at
                val before = pulled
                val back = if (vertical) canScrollVertically(-1) else canScrollHorizontally(-1)
                val forward = if (vertical) canScrollVertically(1) else canScrollHorizontally(1)
                pulled = pullAfter(pulled, delta, back, forward)
                if (pulled != 0f || before != 0f) {
                    child.animate().cancel()
                    val range = (if (vertical) height else width) * MotionSpec.BOUNCE_RANGE
                    val moved = rubberBand(pulled, range)
                    if (vertical) child.translationY = moved else child.translationX = moved
                }
                return@setOnTouchListener pulled != 0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                last = Float.NaN
                if (pulled != 0f) {
                    pulled = 0f
                    child.animate().translationX(0f).translationY(0f).setDuration(MotionSpec.LONG1).setInterpolator(motionEase)
                }
            }
        }
        false
    }
}
