package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.course.Spot

/** 정차 중 코스를 살펴보고 내비에 올리는 화면. */
fun previewScreen(context: Context, course: Course, onBack: () -> Unit, onStart: () -> Unit): View =
    with(context) {
        val details = column().apply {
            addView(label(course.name, Look.TITLE, bold = true))
            if (course.summary.isNotBlank()) addSpaced(label(course.summary, Look.BODY, Look.MUTED), 12)
            addSpaced(label(statsText(course), Look.BODY), 24)
            addSpaced(label(sectionsText(course), Look.BODY, Look.ACCENT), 12)
            if (course.spots.isNotEmpty()) {
                addSpaced(label("스팟", Look.SMALL, Look.MUTED), 32)
                course.spots.forEach { addSpaced(label(spotLine(it), Look.BODY), 8) }
            }
        }
        val left = column().apply {
            addView(
                quietButton("뒤로", onBack),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(ScrollView(context).apply { addView(details) }, fill().apply { topMargin = dp(32) })
            addSpaced(label(naviPlanText(course), Look.SMALL, Look.MUTED), 24)
            addSpaced(primaryButton("내비에 코스 입히기", onStart), 16)
        }
        twoColumns(left, courseMap().apply { show(course.path, naviStops(course)) })
    }

private fun spotLine(spot: Spot): String =
    listOfNotNull(spotKindLabel(spot.kind), spot.name).joinToString(" · ")
