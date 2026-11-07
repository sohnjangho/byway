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

/**
 * 커버가 면을 덮고 아래 띠에 이름, 특색, 구간 막대를 쓰는 카드. 전체가 터치 영역이다.
 * 사진 위 글자는 테마와 무관하게 어두운 띠 위에 밝은 글자로 쓴다.
 */
internal fun Context.coverCard(kit: BrowseKit, course: Course, sizePx: Int): View = FrameLayout(this).apply {
    background = rounded(palette.bgRaised)
    clipToOutline = true
    foreground = pressedVeil()
    setOnClickListener { kit.onCourse(course) }
    addView(cover(course, sizePx), matchFrame())
    val band = column().apply {
        setBackgroundColor(palette.photoScrim)
        setPadding(dp(Dimens.SPACE_4), dp(Dimens.SPACE_2), dp(Dimens.SPACE_4), dp(Dimens.SPACE_3))
        addView(label(course.name, Dimens.BODY, palette.onPhoto, bold = true).atMostLines(1))
        addView(label(coverNote(kmText(kit.book.facts(course).meters), course.tags), Dimens.LABEL, palette.onPhoto).atMostLines(1))
        val bar = sectionBar(kit.book, course).apply { show(course.sections.map { 1f }) }
        addView(bar, LinearLayout.LayoutParams(MATCH, dp(Dimens.CARD_BAR_HEIGHT)).apply { topMargin = dp(Dimens.SPACE_2) })
    }
    addView(band, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
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

/** 옆으로 넘기는 커버 줄. */
internal fun Context.coverRow(cells: List<View>, cellWidth: Int, height: Int): View =
    HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        val line = LinearLayout(context)
        cells.forEachIndexed { i, cell ->
            line.addView(
                cell,
                LinearLayout.LayoutParams(cellWidth, height).apply { if (i > 0) marginStart = dp(Dimens.SPACE_4) },
            )
        }
        addView(line, FrameLayout.LayoutParams(WRAP, height))
    }

/** 비어 있는 묶음을 대신하는 글. 가짜 코스를 채우지 않는다. */
internal fun Context.emptyNote(text: String): View = label(text, Dimens.BODY, palette.inkMuted).apply {
    gravity = Gravity.CENTER
    background = rounded(palette.bgDeep)
    setPadding(dp(Dimens.SPACE_6), dp(Dimens.SPACE_10), dp(Dimens.SPACE_6), dp(Dimens.SPACE_10))
}
