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
    background = rounded(Look.SURFACE, Look.RADIUS_SM)
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

/** 커버가 면을 덮고 아래 띠에 이름과 특색을 쓰는 카드. 전체가 터치 영역이다. */
internal fun Context.coverCard(course: Course, line: String?, sizePx: Int, onClick: () -> Unit): View =
    FrameLayout(this).apply {
        background = rounded(Look.SURFACE)
        clipToOutline = true
        foreground = pressedVeil()
        setOnClickListener { onClick() }
        addView(cover(course, sizePx), matchFrame())
        val band = column().apply {
            setBackgroundColor(Look.COVER_BAND)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(label(course.name, Look.BODY, bold = true).atMostLines(1))
            line?.let { addView(label(it, Look.LABEL, Look.MUTED).atMostLines(1)) }
        }
        addView(band, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
    }

/** 셀을 [columns]칸씩 줄로 쌓는다. 마지막 줄의 빈칸도 자리를 차지해 카드 폭이 같게 유지된다. */
internal fun Context.grid(cells: List<View>, columns: Int, rowHeight: Int): View = column().apply {
    cells.chunked(columns).forEachIndexed { index, chunk ->
        val line = LinearLayout(context)
        repeat(columns) { i ->
            val cell = chunk.getOrNull(i) ?: View(context)
            line.addView(
                cell,
                LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (i > 0) marginStart = dp(Look.CARD_GAP) },
            )
        }
        addView(line, LinearLayout.LayoutParams(MATCH, rowHeight).apply { if (index > 0) topMargin = dp(Look.CARD_GAP) })
    }
}

/** 옆으로 넘기는 커버 줄. */
internal fun Context.coverRow(cells: List<View>, cellWidth: Int, height: Int): View =
    HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        val line = LinearLayout(context)
        cells.forEachIndexed { i, cell ->
            line.addView(
                cell,
                LinearLayout.LayoutParams(cellWidth, height).apply { if (i > 0) marginStart = dp(Look.CARD_GAP) },
            )
        }
        addView(line, FrameLayout.LayoutParams(WRAP, height))
    }

/** 비어 있는 묶음을 대신하는 글. 가짜 코스를 채우지 않는다. */
internal fun Context.emptyNote(text: String): View = label(text, Look.BODY, Look.MUTED).apply {
    gravity = Gravity.CENTER
    background = rounded(Look.SURFACE)
    setPadding(dp(Look.GAP), dp(40), dp(Look.GAP), dp(40))
}
