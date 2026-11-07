package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course

/** 코스를 끝까지 달리고 멈췄을 때 보여 준다. 코스에서 계산한 값만 쓴다. 측정하지 않은 시간이나 속도는 쓰지 않는다. */
internal fun summaryScreen(context: Context, kit: BrowseKit, course: Course, onHome: () -> Unit): View =
    with(context) {
        val facts = kit.book.facts(course)
        val meters = kit.book.sectionMeters(course)
        val details = column().apply {
            addView(label("끝까지 달렸습니다", Dimens.SMALL, palette.accent))
            addView(label(course.name, Dimens.TITLE, bold = true))
            val numbers = listOf(
                Fact(kmText(facts.meters), "코스 거리"),
                Fact("${facts.curves}", "지나온 굽이"),
                Fact(ratioText(facts), "에움 배율"),
                Fact(climbText(course, facts), "오르막"),
            )
            addSpaced(factRow(numbers, Dimens.HEADING), Dimens.SPACE_6)
            course.sections.forEachIndexed { i, section ->
                val gap = if (i == 0) Dimens.SPACE_6 else Dimens.SPACE_2
                addSpaced(sectionLine(i + 1, section.name, section.mood, kmText(meters[i])), gap)
            }
        }
        val buttons = LinearLayout(this).apply {
            addView(primaryButton("홈으로", onHome), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_4) })
            addView(saveToggle(course, Context::quietButton), LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        val text = column().apply {
            addView(ScrollView(context).apply { addView(details) }, fill())
            addSpaced(buttons, Dimens.SPACE_6)
        }
        val drawing = CoursePathView(this).apply {
            asCard()
            show(course, emptyList())
        }
        val edge = dp(kit.edge)
        panes(text, drawing, kit.wide).apply { setPadding(edge, edge, edge, edge) }
    }
