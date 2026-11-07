package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import com.byway.core.course.Course

/** 정차 중 코스를 살펴보고 내비에 올리는 화면. */
internal fun previewScreen(
    context: Context,
    kit: BrowseKit,
    course: Course,
    picture: Picture,
    onPicture: (Picture) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
): View = with(context) {
    val stops = kit.book.stops(course)
    val pane = PicturePane(this, course, stops, picture, onPicture)
    val head = row().apply {
        addView(smallButton("← 뒤로", onBack))
        addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        addView(saveToggle(course, Context::smallButton))
    }
    val scroll = ScrollView(this).apply { addView(details(kit, course)) }
    val foot = column().apply {
        addView(label(naviPlanText(course, stops), Dimens.LABEL, palette.inkMuted).tabular())
        addSpaced(primaryButton("내비에 코스 입히기", onStart), Dimens.SPACE_3)
        addSpaced(label("창 막대를 끌면 내비와 나란히 볼 수 있습니다", Dimens.LABEL, palette.inkMuted), Dimens.SPACE_2)
    }
    val edge = dp(kit.edge)
    if (kit.wide) {
        LinearLayout(this).apply {
            setPadding(edge, dp(Dimens.SPACE_6), edge, dp(Dimens.SPACE_6))
            val text = column().apply {
                addView(head)
                addView(scroll, fill().apply { topMargin = dp(Dimens.SPACE_3) })
                addSpaced(foot, Dimens.SPACE_4)
            }
            addView(text, LinearLayout.LayoutParams(dp(Dimens.TEXT_PANE_WIDTH), MATCH).apply { marginEnd = dp(Dimens.SPACE_10) })
            addView(pane, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    } else {
        column().apply {
            setPadding(edge, dp(Dimens.SPACE_4), edge, dp(Dimens.SPACE_4))
            addView(head)
            addView(pane, LinearLayout.LayoutParams(MATCH, dp(Dimens.PICTURE_NARROW_HEIGHT)).apply { topMargin = dp(Dimens.SPACE_2) })
            addView(scroll, fill().apply { topMargin = dp(Dimens.SPACE_4) })
            addSpaced(foot, Dimens.SPACE_4)
        }
    }
}

private fun Context.details(kit: BrowseKit, course: Course): View = column().apply {
    val facts = kit.book.facts(course)
    addView(label(kit.book.character(course), Dimens.SMALL, palette.accent))
    addView(label(course.name, Dimens.TITLE, bold = true))
    if (course.summary.isNotBlank()) addView(label(course.summary, Dimens.BODY, palette.inkSecondary))
    val numbers = listOf(
        Fact(kmText(facts.meters), "거리"),
        Fact("${facts.curves}", "굽이"),
        Fact(ratioText(facts), "에움 배율"),
        Fact(topText(course, facts), "최고 고도"),
    )
    addSpaced(factRow(numbers, Dimens.HEADING), Dimens.SPACE_4)
    val meters = kit.book.sectionMeters(course)
    elevationChart(course, meters)?.let { chart ->
        val height = dp(if (kit.wide) Dimens.CHART_HEIGHT else Dimens.CHART_NARROW_HEIGHT)
        addView(chart, LinearLayout.LayoutParams(MATCH, height).apply { topMargin = dp(Dimens.SPACE_4) })
    }
    course.sections.forEachIndexed { i, section ->
        val gap = if (i == 0) Dimens.SPACE_4 else Dimens.SPACE_2
        addSpaced(sectionLine(i + 1, section.name, section.mood, kmText(meters[i])), gap)
    }
}
