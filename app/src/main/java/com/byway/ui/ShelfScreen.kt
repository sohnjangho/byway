package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import com.byway.core.course.Course

/** 칩을 고르면 그 묶음의 코스 격자를 보여 준다. 테마 탭과 지역 탭이 같은 틀을 쓴다. */
internal fun shelfScreen(
    context: Context,
    kit: BrowseKit,
    choices: List<Choice>,
    selected: String?,
    empty: String,
    coursesOf: (String) -> List<Course>,
    onPick: (String) -> Unit,
): View = with(context) {
    if (choices.isEmpty()) {
        return FrameLayout(this).apply {
            val edge = dp(kit.edge)
            setPadding(edge, edge, edge, edge)
            addView(emptyNote(empty), FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        }
    }
    val chosen = selected?.takeIf { key -> choices.any { it.key == key } } ?: choices.first().key
    val scroll = ScrollView(this).apply {
        clipToPadding = false
        setPadding(0, 0, 0, dp(Look.WIDE_GAP))
    }
    fun show(key: String) {
        scroll.removeAllViews()
        scroll.addView(courseGrid(kit, coursesOf(key)))
    }
    show(chosen)
    column().apply {
        setPadding(dp(kit.edge), dp(Look.GAP), dp(kit.edge), 0)
        addView(
            chipRow(choices, chosen, Look.BODY) { key ->
                onPick(key)
                show(key)
            },
        )
        addView(scroll, fill().apply { topMargin = dp(Look.CARD_GAP) })
    }
}
