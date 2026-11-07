package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** 수치 칸 하나. 값 아래에 작은 이름을 둔다. */
internal class Fact(val value: String, val name: String)

internal fun Context.factCell(fact: Fact, size: Float): View = column().apply {
    addView(label(fact.value, size, bold = true).tabular().atMostLines(1))
    addView(label(fact.name, Look.LABEL, Look.MUTED).atMostLines(1))
}

/** 수치 칸을 같은 폭으로 한 줄에 둔다. */
internal fun Context.factRow(facts: List<Fact>, size: Float): View = LinearLayout(this).apply {
    facts.forEachIndexed { i, fact ->
        addView(
            factCell(fact, size),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(12) },
        )
    }
}

/** 구간 한 줄. 세 구간 색은 밝기가 비슷하므로 색점 옆에 이름을 항상 함께 쓴다. */
internal fun Context.sectionLine(name: String, mood: String?, distance: String, size: Float): View = row().apply {
    val dot = View(context).apply { background = rounded(moodColor(mood), 8) }
    addView(dot, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(16) })
    addView(label(name, size).atMostLines(1), LinearLayout.LayoutParams(0, WRAP, 1f))
    addView(label(distance, size, Look.MUTED).tabular().apply { gravity = Gravity.END })
}
