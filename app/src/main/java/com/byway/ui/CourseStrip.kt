package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.byway.core.course.Course

/** 홈 아래 코스 띠. 고른 코스가 보이는 자리로 스크롤해 둔다. */
internal fun Context.courseStrip(kit: BrowseKit, chosen: Course, previous: Int?, onPick: (Course, Int) -> Unit): View {
    val scroller = HorizontalScrollView(this).bounceAtEnds(vertical = false)
    val row = LinearLayout(this)
    var chosenIndex = 0
    kit.book.courses.forEachIndexed { i, course ->
        if (course.id == chosen.id) chosenIndex = i
        val size = LinearLayout.LayoutParams(dp(Dimens.STRIP_CARD_WIDTH), dp(Dimens.STRIP_CARD_HEIGHT)).apply {
            if (i > 0) marginStart = dp(Dimens.SPACE_3)
        }
        row.addView(stripCard(course, course.id == chosen.id) { onPick(course, scroller.scrollX) }, size)
    }
    return scroller.apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
        post { scrollTo(stripScrollFor(previous, chosenIndex, dp(Dimens.STRIP_CARD_WIDTH + Dimens.SPACE_3), dp(Dimens.STRIP_CARD_WIDTH), width), 0) }
    }
}

private fun Context.stripCard(course: Course, on: Boolean, onClick: () -> Unit): View = row().apply {
    background = if (on) {
        rounded(palette.bgRaised, Dimens.RADIUS_MD, palette.accent)
    } else {
        rounded(palette.bgRaised, Dimens.RADIUS_MD)
    }
    val inset = dp(Dimens.SPACE_2)
    setPadding(inset, inset, inset, inset)
    pressMotion()
    setOnClickListener { onClick() }
    addView(
        cover(course, dp(Dimens.STRIP_THUMB)),
        LinearLayout.LayoutParams(dp(Dimens.STRIP_THUMB), dp(Dimens.STRIP_THUMB)).apply { marginEnd = dp(Dimens.SPACE_3) },
    )
    val text = column().apply {
        addView(label(course.name, Dimens.SMALL, bold = true).atMostLines(1))
        addView(label(pickLabels(course.tags).firstOrNull().orEmpty(), Dimens.LABEL, palette.inkSecondary).atMostLines(1))
    }
    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
}
