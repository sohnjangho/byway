package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked

/** 코스에서 계산한 수치로 줄을 세운다. 인기 순위가 아니므로 화면에서 그렇게 밝힌다. */
internal fun rankingScreen(context: Context, kit: BrowseKit, by: RankBy, onBy: (RankBy) -> Unit): View = with(context) {
    val note = label("", Look.SMALL, Look.MUTED)
    val rows = column()
    fun show(measure: RankBy) {
        note.text = rankNote(measure)
        rows.removeAllViews()
        kit.book.ranked(measure).forEachIndexed { i, ranked ->
            rows.addSpaced(rankRow(kit, i + 1, ranked, measure), if (i == 0) 0 else 12)
        }
    }
    show(by)
    val choices = RankBy.entries.map { Choice(it.name, rankTitle(it)) }
    column().apply {
        setPadding(dp(kit.edge), dp(Look.GAP), dp(kit.edge), 0)
        addView(
            chipRow(choices, by.name, Look.BODY) { name ->
                val measure = RankBy.valueOf(name)
                onBy(measure)
                show(measure)
            },
        )
        addSpaced(note, 12)
        val scroll = ScrollView(context).apply {
            clipToPadding = false
            setPadding(0, 0, 0, dp(Look.WIDE_GAP))
            addView(rows)
        }
        addView(scroll, fill().apply { topMargin = dp(Look.CARD_GAP) })
    }
}

private fun Context.rankRow(kit: BrowseKit, place: Int, ranked: Ranked, by: RankBy): View = row().apply {
    val course = ranked.course
    background = pressable(Look.SURFACE, Look.SURFACE_PRESSED)
    setPadding(dp(16), dp(12), dp(32), dp(12))
    setOnClickListener { kit.onCourse(course) }
    val number = label("$place", Look.TITLE, if (place == 1) Look.ACCENT else Look.INK, bold = true).tabular().apply {
        gravity = Gravity.CENTER
    }
    addView(number, LinearLayout.LayoutParams(dp(if (kit.wide) 88 else 72), WRAP))
    val width = dp(if (kit.wide) 170 else 120)
    val height = dp(if (kit.wide) 150 else 120)
    addView(cover(course, width), LinearLayout.LayoutParams(width, height).apply { marginEnd = dp(Look.GAP) })
    val text = column().apply {
        addView(label(course.name, Look.HEADING, bold = true).atMostLines(1))
        addView(label(kit.book.character(course), Look.SMALL, Look.MUTED).atMostLines(1))
    }
    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(16) })
    val size = if (kit.wide) Look.TITLE else Look.HEADING
    addView(label(rankValue(by, ranked), size, bold = true).tabular().apply { gravity = Gravity.END })
}
