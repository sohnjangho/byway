package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.byway.core.course.Course

/** 코스 커버. 파일이 없으면 코스 그림이 그 자리를 채운다. */
internal fun Context.cover(course: Course, sizePx: Int): View = FrameLayout(this).apply {
    background = rounded(palette.bgRaised, Dimens.RADIUS_SM)
    clipToOutline = true
    addView(CoursePathView(context).apply { show(course, emptyList(), thin = true) }, matchFrame())
    Covers.load(context, course.id, sizePx) { bitmap ->
        removeAllViews()
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }
        addView(image, matchFrame())
    }
}

/** 셀을 [columns]칸씩 줄로 쌓는다. 마지막 줄의 빈칸도 자리를 차지해 카드 폭이 같게 유지된다. */
internal fun Context.grid(cells: List<View>, columns: Int, rowHeight: Int, gap: Int = Dimens.SPACE_4): View = column().apply {
    cells.chunked(columns).forEachIndexed { index, chunk ->
        val line = LinearLayout(context)
        repeat(columns) { i ->
            val cell = chunk.getOrNull(i) ?: View(context)
            line.addView(cell, LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (i > 0) marginStart = dp(gap) })
        }
        addView(line, LinearLayout.LayoutParams(MATCH, rowHeight).apply { if (index > 0) topMargin = dp(gap) })
    }
}

/** 비어 있는 묶음을 대신하는 글. 가짜 코스를 채우지 않는다. */
internal fun Context.emptyNote(text: String): View = label(text, Dimens.BODY, palette.inkMuted).apply {
    gravity = Gravity.CENTER
    background = rounded(palette.bgDeep)
    setPadding(dp(Dimens.SPACE_6), dp(Dimens.SPACE_10), dp(Dimens.SPACE_6), dp(Dimens.SPACE_10))
}
