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
    val note = label("", Dimens.SMALL, palette.inkMuted)
    val edge = dp(kit.edge)
    // 카드 그림자가 나올 자리를 두려고 양옆 여백을 줄 묶음 안쪽에 둔다.
    val rows = column().apply { setPadding(edge, 0, edge, dp(Dimens.SPACE_8)) }
    fun show(measure: RankBy) {
        note.text = "${rankNote(measure)} · $PICK_NOTE"
        rows.removeAllViews()
        kit.book.ranked(measure).forEachIndexed { i, ranked ->
            rows.addSpaced(rankRow(kit, i + 1, ranked, measure), if (i == 0) 0 else Dimens.SPACE_3)
        }
    }
    show(by)
    val choices = RankBy.entries.map { Choice(it.name, rankTitle(it)) }
    val head = column().apply {
        setPadding(edge, dp(Dimens.SPACE_6), edge, 0)
        addView(
            chipRow(choices, by.name, Dimens.BODY) { name ->
                val measure = RankBy.valueOf(name)
                onBy(measure)
                show(measure)
            },
        )
        addSpaced(note, Dimens.SPACE_3)
    }
    column().apply {
        addView(head)
        addView(ScrollView(context).apply { addView(rows) }, fill().apply { topMargin = dp(Dimens.SPACE_4) })
    }
}

private fun Context.rankRow(kit: BrowseKit, place: Int, ranked: Ranked, by: RankBy): View = row().apply {
    val course = ranked.course
    asCard(clickable = true)
    softBackground(course)
    foreground = pressedVeil()
    setPadding(dp(Dimens.SPACE_4), dp(Dimens.SPACE_3), dp(Dimens.SPACE_8), dp(Dimens.SPACE_3))
    setOnClickListener { kit.onCourse(course) }
    val side = dp(if (kit.wide) Dimens.RANK_NUMBER_WIDE else Dimens.RANK_NUMBER_NARROW)
    val number = label("$place", Dimens.HEADING, if (place == 1) palette.accent else palette.ink, bold = true).tabular().apply {
        gravity = Gravity.CENTER
        background = rounded(palette.bgRaised, Dimens.RADIUS_SM)
    }
    addView(number, LinearLayout.LayoutParams(side, side).apply { marginEnd = dp(Dimens.SPACE_4) })
    val width = dp(if (kit.wide) Dimens.RANK_COVER_WIDE_W else Dimens.RANK_COVER_NARROW)
    val height = dp(if (kit.wide) Dimens.RANK_COVER_WIDE_H else Dimens.RANK_COVER_NARROW)
    addView(cover(course, width), LinearLayout.LayoutParams(width, height).apply { marginEnd = dp(Dimens.SPACE_6) })
    val name = label(course.name, Dimens.HEADING, bold = true).atMostLines(1)
    val text = column().apply {
        if (kit.wide) {
            val title = row().apply {
                addView(name, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(Dimens.SPACE_4) })
                addView(pickRow(course.tags))
            }
            addView(title)
        } else {
            addView(name)
            addSpaced(pickRow(course.tags, limit = 2), Dimens.SPACE_2)
        }
        if (course.summary.isNotBlank()) addSpaced(label(course.summary, Dimens.SMALL).atMostLines(1), Dimens.SPACE_2)
    }
    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_4) })
    val value = column().apply {
        gravity = Gravity.END
        addView(label(rankValue(by, ranked), Dimens.VALUE, bold = true).tabular())
        addView(label(rankTitle(by), Dimens.LABEL))
    }
    addView(value, LinearLayout.LayoutParams(WRAP, WRAP))
}
