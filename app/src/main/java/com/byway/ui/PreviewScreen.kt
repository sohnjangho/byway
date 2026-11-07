package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import com.byway.core.course.Course
import com.byway.service.MyDrives

private const val PICTURE_HEIGHT = 260

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
        addView(label(naviPlanText(course, stops), Look.LABEL, Look.MUTED).tabular())
        addSpaced(primaryButton("내비에 코스 입히기", onStart), 12)
        addSpaced(label("창 막대를 끌면 내비와 나란히 볼 수 있습니다", Look.LABEL, Look.MUTED), 8)
    }
    val edge = dp(kit.edge)
    if (kit.wide) {
        LinearLayout(this).apply {
            setPadding(edge, dp(Look.GAP), edge, dp(Look.GAP))
            val text = column().apply {
                addView(head)
                addView(scroll, fill().apply { topMargin = dp(12) })
                addSpaced(foot, Look.CARD_GAP)
            }
            addView(text, LinearLayout.LayoutParams(dp(640), MATCH).apply { marginEnd = dp(Look.EDGE) })
            addView(pane, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    } else {
        column().apply {
            setPadding(edge, dp(Look.CARD_GAP), edge, dp(Look.CARD_GAP))
            addView(head)
            addView(pane, LinearLayout.LayoutParams(MATCH, dp(PICTURE_HEIGHT)).apply { topMargin = dp(8) })
            addView(scroll, fill().apply { topMargin = dp(Look.CARD_GAP) })
            addSpaced(foot, Look.CARD_GAP)
        }
    }
}

private fun Context.details(kit: BrowseKit, course: Course): View = column().apply {
    val facts = kit.book.facts(course)
    addView(label(kit.book.character(course), Look.SMALL, Look.ACCENT))
    addView(label(course.name, Look.TITLE, bold = true))
    if (course.summary.isNotBlank()) addView(label(course.summary, Look.BODY, Look.MUTED))
    val numbers = listOf(
        Fact(kmText(facts.meters), "거리"),
        Fact("${facts.curves}", "굽이"),
        Fact(ratioText(facts), "에움 배율"),
        Fact(topText(course, facts), "최고 고도"),
    )
    addSpaced(factRow(numbers, Look.HEADING), Look.CARD_GAP)
    val meters = kit.book.sectionMeters(course)
    val chart = ElevationView(context).apply { sections(course.elevation, meters, course.sections.map { it.mood }) }
    addView(chart, LinearLayout.LayoutParams(MATCH, dp(100)).apply { topMargin = dp(Look.CARD_GAP) })
    course.sections.forEachIndexed { i, section ->
        addSpaced(sectionLine(section.name, section.mood, kmText(meters[i]), Look.BODY), if (i == 0) Look.CARD_GAP else 6)
    }
}
