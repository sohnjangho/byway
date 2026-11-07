package com.byway.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.View
import android.widget.LinearLayout

/** 수치 칸 하나. 이름 아래에 값을 두고 단위는 작게 쓴다. */
internal class Fact(val value: String, val name: String)

internal fun Context.factCell(fact: Fact, size: Float): View = column().apply {
    background = rounded(palette.bgElevated, Dimens.RADIUS_SM, palette.lineSubtle)
    setPadding(dp(Dimens.SPACE_4), dp(Dimens.SPACE_2), dp(Dimens.SPACE_3), dp(Dimens.SPACE_2))
    addView(label(fact.name, Dimens.LABEL, palette.inkMuted).atMostLines(1))
    addView(label(withSmallUnit(fact.value), size, bold = true).tabular().atMostLines(1))
}

private fun withSmallUnit(value: String): CharSequence {
    val start = unitStart(value)
    if (start == value.length) return value
    return SpannableString(value).apply {
        setSpan(AbsoluteSizeSpan(Dimens.LABEL.toInt(), true), start, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

/** 수치 칸을 같은 폭으로 한 줄에 둔다. */
internal fun Context.factRow(facts: List<Fact>, size: Float): View = LinearLayout(this).apply {
    facts.forEachIndexed { i, fact ->
        addView(
            factCell(fact, size),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(Dimens.SPACE_3) },
        )
    }
}

/** 구간 색 점. 세 구간 색은 밝기가 비슷하므로 옆에 글자를 항상 함께 쓴다. */
internal fun Context.sectionDot(mood: String?): View =
    View(this).apply { background = rounded(palette.moodColor(mood), Dimens.SPACE_2) }

/** 구간 한 줄. 번호, 색 점, 코스 파일의 구간 이름, 거리를 쓴다. */
internal fun Context.sectionLine(number: Int, name: String, mood: String?, distance: String): View = row().apply {
    background = rounded(palette.bgDeep, Dimens.RADIUS_SM)
    minimumHeight = dp(Dimens.SECTION_ROW_HEIGHT)
    setPadding(dp(Dimens.SPACE_4), 0, dp(Dimens.SPACE_4), 0)
    addView(sectionDot(mood), LinearLayout.LayoutParams(dp(Dimens.DOT), dp(Dimens.DOT)).apply { marginEnd = dp(Dimens.SPACE_3) })
    addView(label("$number", Dimens.BODY, palette.inkMuted).tabular(), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(Dimens.SPACE_3) })
    addView(label(name, Dimens.BODY).atMostLines(1), LinearLayout.LayoutParams(0, WRAP, 1f))
    addView(label(distance, Dimens.BODY, palette.inkSecondary).tabular())
}
