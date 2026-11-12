package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.course.funScore
import com.byway.core.course.naturePercent

/** 코스를 끝까지 달리고 멈췄을 때 보여 준다. 코스에서 계산한 값만 쓴다. 측정하지 않은 시간이나 속도는 쓰지 않는다. */
internal fun summaryScreen(context: Context, kit: BrowseKit, course: Course, onHome: () -> Unit): View =
    with(context) {
        val facts = kit.book.facts(course)
        val meters = kit.book.sectionMeters(course)
        val details = column().apply {
            addView(label("끝까지 달렸습니다", Dimens.SMALL, palette.inkSecondary))
            addView(label(withDummyMark(course, course.name), Dimens.TITLE, bold = true))
            val numbers = listOf(
                Fact(kmText(facts.meters), "코스 거리"),
                Fact(measured(course, "${facts.curves}"), "지나온 커브"),
                Fact(measured(course, "${funScore(facts, course.elevation.isNotEmpty())}점"), "운전 즐거움"),
                Fact(measured(course, "${naturePercent(facts.moodShare)}%"), "도심 밖"),
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
            addView(ScrollView(context).apply { addView(details) }.bounceAtEnds(vertical = true), fill())
            addSpaced(buttons, Dimens.SPACE_6)
        }
        // 흰 카드 대신 코스 분위기 위에 코스 선을 바로 그린다(화면 다듬기 설계 4절).
        val drawing = CoursePathView(this).apply { show(course, emptyList()) }
        val edge = dp(kit.edge)
        MoodLayer(this, kit.window).hold(panes(text, drawing, kit.wide).apply { setPadding(edge, edge, edge, edge) }).apply { show(course) }
    }
