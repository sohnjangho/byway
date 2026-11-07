package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course

/** 코스를 끝까지 달리고 멈췄을 때 보여 준다. 기록에 없는 값(걸린 시간 등)은 쓰지 않는다. */
internal fun summaryScreen(context: Context, kit: BrowseKit, course: Course, onHome: () -> Unit): View =
    with(context) {
        val facts = kit.book.facts(course)
        val meters = kit.book.sectionMeters(course)
        val details = column().apply {
            addView(label("끝까지 달렸습니다", Look.SMALL, Look.ACCENT))
            addView(label(course.name, Look.TITLE, bold = true))
            val numbers = listOf(
                Fact(kmText(facts.meters), "달린 거리"),
                Fact("${course.sections.size}구간", "끝까지 따라감"),
            )
            addSpaced(factRow(numbers, Look.TITLE), Look.GAP)
            course.sections.forEachIndexed { i, section ->
                addSpaced(sectionLine(section.name, section.mood, kmText(meters[i]), Look.BODY), if (i == 0) Look.GAP else 8)
            }
        }
        val buttons = LinearLayout(this).apply {
            addView(primaryButton("홈으로", onHome), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Look.CARD_GAP) })
            addView(saveToggle(course, Context::quietButton), LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        val text = column().apply {
            addView(ScrollView(context).apply { addView(details) }, fill())
            addSpaced(buttons, Look.GAP)
        }
        val drawing = CoursePathView(this).apply {
            background = rounded(Look.SURFACE)
            show(course, emptyList())
        }
        val edge = dp(if (kit.wide) Look.EDGE else Look.NARROW_EDGE)
        panes(text, drawing, kit.wide).apply { setPadding(edge, edge, edge, edge) }
    }
