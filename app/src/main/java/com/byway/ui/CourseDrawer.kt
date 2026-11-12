package com.byway.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import androidx.core.animation.doOnEnd
import com.byway.core.course.Course

/**
 * 홈에서 다른 코스를 고르는 서랍. 넓은 창은 운전석 쪽 글 칸 위로 왼쪽에서, 1/3 창은 아래에서 나온다.
 * 닫기나 서랍 바깥을 누르면 닫힌다. 정차 화면에만 있다.
 */
@SuppressLint("ViewConstructor")
internal class CourseDrawer(
    context: Context,
    private val kit: BrowseKit,
    private val chosen: Course,
    private val onPick: (Course) -> Unit,
) : FrameLayout(context) {
    private val colors = context.palette
    private val edge = drawerEdge(kit.window)
    private val scrim = View(context)
    private val panel = context.column()
    private val scroll = ScrollView(context)
    private var sliding: ValueAnimator? = null

    /** 열려 있거나 열리는 중인지. 닫히는 도중에 다시 열면 그 자리에서 되돌아 나온다. */
    private var wanted = false

    init {
        // 닫혀 있어도 크기를 재 두어야 열 때 밀려 나올 거리를 안다. 보이지 않는 동안에는 터치를 받지 않는다.
        visibility = INVISIBLE
        scrim.setBackgroundColor(colors.softWash)
        scrim.setOnClickListener { close() }
        addView(scrim, matchFrame())

        val inset = context.dp(if (edge == DrawerEdge.LEFT) Dimens.SPACE_8 else Dimens.SPACE_6)
        panel.setPadding(inset, context.dp(Dimens.SPACE_6), inset, 0)
        panel.isClickable = true
        panel.background = face()
        val header = context.row().apply {
            addView(context.label("다른 코스", Dimens.BODY, bold = true))
            addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(context.smallButton("닫기") { close() })
        }
        panel.addView(header)
        val list = context.column()
        drawerOrder(kit.book.courses, chosen.id) { it.id }.forEachIndexed { i, course ->
            list.addSpaced(row(course), if (i == 0) 0 else Dimens.SPACE_2)
        }
        list.setPadding(0, 0, 0, context.dp(Dimens.SPACE_6))
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list)
        scroll.bounceAtEnds(vertical = true)
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(Dimens.SPACE_3) })
        addView(
            panel,
            when (edge) {
                DrawerEdge.LEFT -> LayoutParams(context.dp(Dimens.SIDE_WIDTH), MATCH).apply { leftMargin = context.dp(Dimens.RAIL_WIDTH) }
                DrawerEdge.BOTTOM -> LayoutParams(MATCH, MATCH).apply { topMargin = context.dp(Dimens.DRAWER_SHEET_TOP) }
            },
        )
    }

    fun open() {
        if (wanted) return
        wanted = true
        if (sliding == null) {
            // 지금 코스가 맨 위에 있으므로 열 때마다 처음부터 보여 준다.
            scroll.scrollTo(0, 0)
            place(0f)
        }
        visibility = VISIBLE
        slide(1f) {}
    }

    fun close() {
        if (!wanted) return
        wanted = false
        slide(0f) { visibility = INVISIBLE }
    }

    override fun onDetachedFromWindow() {
        stopSliding()
        super.onDetachedFromWindow()
    }

    /** 취소해도 끝났을 때의 일이 불리므로 먼저 떼어 낸다. 닫히다 만 서랍이 갑자기 사라지지 않게 한다. */
    private fun stopSliding() {
        sliding?.removeAllListeners()
        sliding?.cancel()
        sliding = null
    }

    private fun slide(to: Float, onEnd: () -> Unit) {
        val from = if (sliding != null) scrim.alpha else 1f - to
        stopSliding()
        sliding = ValueAnimator.ofFloat(from, to).apply {
            duration = MotionSpec.MEDIUM2
            interpolator = motionEase
            addUpdateListener { place(it.animatedValue as Float) }
            doOnEnd {
                sliding = null
                onEnd()
            }
            start()
        }
    }

    private fun place(shown: Float) {
        val (dx, dy) = drawerShift(edge, shown, panel.width.toFloat(), panel.height.toFloat())
        panel.translationX = dx
        panel.translationY = dy
        scrim.alpha = shown
    }

    /** 아래에서 올라오는 서랍은 위쪽 모서리만 둥글다. */
    private fun face() = GradientDrawable().apply {
        setColor(context.palette.bgRaised)
        if (edge == DrawerEdge.BOTTOM) {
            val r = context.dp(Dimens.RADIUS_LG).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    private fun row(course: Course): View = context.row().apply {
        val on = course.id == chosen.id
        background = if (on) {
            context.rounded(colors.bgElevated, Dimens.RADIUS_MD, colors.accent)
        } else {
            context.pressable(colors.bgRaised, colors.bgPressed)
        }
        val inset = context.dp(Dimens.SPACE_2)
        setPadding(inset, inset, context.dp(Dimens.SPACE_4), inset)
        pressMotion()
        setOnClickListener { if (on) close() else onPick(course) }
        addView(
            context.cover(course, context.dp(Dimens.STRIP_THUMB)),
            LinearLayout.LayoutParams(context.dp(Dimens.STRIP_THUMB), context.dp(Dimens.STRIP_THUMB)).apply { marginEnd = context.dp(Dimens.SPACE_4) },
        )
        val text = context.column().apply {
            addView(context.label(course.name, Dimens.SMALL, bold = true).atMostLines(1))
            val line = courseLine(course.region, kit.book.facts(course).meters, pickLabels(course.tags).firstOrNull().orEmpty())
            addView(context.label(line, Dimens.LABEL, colors.inkSecondary).atMostLines(1))
        }
        addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (on) addView(context.label("지금 코스", Dimens.LABEL, colors.inkSecondary).atMostLines(1))
    }
}
