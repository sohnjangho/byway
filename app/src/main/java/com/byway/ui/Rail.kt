package com.byway.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.view.OneShotPreDrawListener

private const val ENTER_DP = 24

/** 전환이 멈춘 듯 보이지 않도록 그림을 기다리는 시간의 한도. */
private const val PICTURE_WAIT_MS = 120L

/**
 * 메뉴와 그 아래 내용. 전체와 2/3 창은 왼쪽, 1/3 창은 위쪽이다.
 * [over]면 메뉴가 콘텐츠 위에 반투명으로 얹히고 콘텐츠는 메뉴 자리까지 채운다.
 * 메뉴는 그대로 두고 내용만 [swap]으로 바꿔 화면이 끊기지 않게 한다.
 */
@SuppressLint("ViewConstructor")
internal class RailFrame(
    context: Context,
    selected: Tab,
    window: WindowClass,
    private val onTab: (Tab) -> Unit,
    content: View,
    private var over: Boolean,
) : FrameLayout(context) {
    private val onTop = window == WindowClass.THIRD
    private val colors = context.palette
    private val backdrop = RailBackdrop(context)
    private val body = if (onTop) context.row() else context.column()
    private val items = mutableMapOf<Tab, TextView>()
    private val avatarMark = context.label("나", Dimens.SMALL, bold = true)
    private var shown = content
    private var entering: View? = null
    private var enteringStarted = false

    init {
        setBackgroundColor(colors.bg)
        addView(content, inset())
        buildRail()
        backdrop.addView(body, matchFrame())
        val size = if (onTop) {
            LayoutParams(MATCH, context.dp(Dimens.RAIL_TOP_HEIGHT))
        } else {
            LayoutParams(context.dp(Dimens.RAIL_WIDTH), MATCH)
        }
        addView(backdrop, size)
        mark(selected, animate = false)
    }

    private fun inset() = LayoutParams(MATCH, MATCH).apply {
        if (!over && onTop) topMargin = context.dp(Dimens.RAIL_TOP_HEIGHT)
        if (!over && !onTop) leftMargin = context.dp(Dimens.RAIL_WIDTH)
    }

    /** [sign]은 내용이 들어오는 쪽이다. 0이면 제자리에서 나타난다. 전환 중에 또 눌러도 바로 다음 화면으로 넘어간다. */
    fun swap(next: View, selected: Tab, over: Boolean, sign: Int) {
        this.over = over
        mark(selected, animate = true)
        settleEntering()
        // 들어오는 동안 빈 곳을 눌러도 아래의 이전 화면이 눌리지 않게 한다.
        next.isClickable = true
        addView(next, childCount - 1, inset())
        entering = next
        enteringStarted = false
        next.alpha = 0f
        val shift = (context.dp(ENTER_DP) * sign).toFloat()
        if (onTop) next.translationX = shift else next.translationY = shift
        Covers.afterReads(PICTURE_WAIT_MS) {
            if (entering !== next) return@afterReads
            enteringStarted = true
            next.animate().alpha(1f).translationX(0f).translationY(0f)
                .setDuration(MotionSpec.MEDIUM2)
                .setInterpolator(motionEase)
                .withLayer()
                .withEndAction { if (entering === next) finishEntering(next) }
        }
    }

    private fun settleEntering() {
        val view = entering ?: return
        if (enteringStarted) {
            view.animate().cancel()
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            finishEntering(view)
        } else {
            removeView(view)
            entering = null
        }
    }

    private fun finishEntering(view: View) {
        removeView(shown)
        shown = view
        entering = null
    }

    private fun buildRail() = body.apply {
        // 메뉴 뒤로 터치가 지나가지 않게 한다.
        isClickable = true
        if (onTop) {
            setPadding(context.dp(Dimens.SPACE_6), context.dp(Dimens.SPACE_2), context.dp(Dimens.SPACE_6), context.dp(Dimens.SPACE_2))
        } else {
            setPadding(context.dp(Dimens.SPACE_3), context.dp(Dimens.SPACE_6), context.dp(Dimens.SPACE_3), context.dp(Dimens.SPACE_6))
        }
        val brand = context.label(context.applicationInfo.loadLabel(context.packageManager), Dimens.BRAND, colors.accent).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = context.dp(Dimens.TOUCH_MIN)
            pressMotion()
            setOnClickListener { onTab(Tab.HOME) }
        }
        val brandSize = if (onTop) {
            LinearLayout.LayoutParams(WRAP, MATCH).apply { marginEnd = context.dp(Dimens.SPACE_4) }
        } else {
            LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = context.dp(Dimens.SPACE_6) }
        }
        addView(brand, brandSize)
        Tab.entries.forEach { tab ->
            val size = if (onTop) {
                LinearLayout.LayoutParams(WRAP, context.dp(Dimens.TOUCH_MIN))
            } else {
                LinearLayout.LayoutParams(MATCH, context.dp(Dimens.RAIL_ITEM_HEIGHT)).apply { bottomMargin = context.dp(Dimens.SPACE_2) }
            }
            addView(railItem(tab).also { items[tab] = it }, size)
        }
        addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        addView(avatar(), LinearLayout.LayoutParams(if (onTop) WRAP else MATCH, WRAP))
    }

    private fun railItem(tab: Tab): TextView = context.label(tab.title, Dimens.SMALL).apply {
        gravity = Gravity.CENTER
        if (onTop) setPadding(context.dp(Dimens.SPACE_4), 0, context.dp(Dimens.SPACE_4), 0)
        pressMotion()
        setOnClickListener { onTab(tab) }
    }

    private fun avatar(): View = FrameLayout(context).apply {
        minimumHeight = context.dp(Dimens.TOUCH_MIN)
        pressMotion()
        setOnClickListener { onTab(Tab.MINE) }
        avatarMark.gravity = Gravity.CENTER
        addView(avatarMark, LayoutParams(context.dp(Dimens.AVATAR_SMALL), context.dp(Dimens.AVATAR_SMALL), Gravity.CENTER))
    }

    private fun mark(selected: Tab, animate: Boolean) {
        backdrop.setBackgroundColor(if (over) colors.softWash else colors.bgRaised)
        items.forEach { (tab, item) ->
            val on = tab == selected
            item.setTextColor(if (on || over) colors.ink else colors.inkSecondary)
            item.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            item.background = if (on) null else context.pressable(Color.TRANSPARENT, colors.bgPressed)
        }
        val mine = selected == Tab.MINE
        avatarMark.setTextColor(if (mine) colors.onAccent else colors.accent)
        avatarMark.background = context.rounded(if (mine) colors.accent else colors.bgPressed, Dimens.AVATAR_SMALL / 2)
        val item = items.getValue(selected)
        // 1/3 창은 굵은 글자로 칸 폭이 바뀌므로 배치가 끝난 뒤에 자리를 읽는다.
        OneShotPreDrawListener.add(body) {
            backdrop.moveTo(MarkBox(item.left.toFloat(), item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat()), animate)
        }
        body.invalidate()
    }
}

/** 메뉴 바탕과 선택 표시. 표시는 메뉴 글자 아래에 그려지고 칸 사이를 미끄러져 옮겨 간다. */
private class RailBackdrop(context: Context) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.palette.accentSoft }
    private val radius = context.dp(Dimens.RADIUS_MD).toFloat()
    private var box: MarkBox? = null
    private var mover: ValueAnimator? = null

    init {
        setWillNotDraw(false)
    }

    fun moveTo(target: MarkBox, animate: Boolean) {
        mover?.cancel()
        val from = box
        if (!animate || from == null) {
            box = target
            invalidate()
            return
        }
        mover = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionSpec.MEDIUM2
            interpolator = motionEase
            addUpdateListener {
                box = from.toward(target, it.animatedFraction)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        box?.let { canvas.drawRoundRect(it.left, it.top, it.right, it.bottom, radius, radius, paint) }
    }

    override fun onDetachedFromWindow() {
        mover?.cancel()
        super.onDetachedFromWindow()
    }
}
