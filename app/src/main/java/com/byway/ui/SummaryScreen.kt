package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.Space
import com.byway.core.course.Course

/** 코스를 끝까지 달리고 멈췄을 때 보여 준다. */
fun summaryScreen(context: Context, course: Course, onHome: () -> Unit): View =
    with(context) {
        val left = column().apply {
            addView(label("완주했습니다", Look.BODY, Look.ACCENT))
            addSpaced(label(course.name, Look.TITLE, bold = true), 12)
            addSpaced(label(statsText(course), Look.HEADING), 24)
            addSpaced(label(sectionsText(course), Look.BODY, Look.MUTED), 12)
            addView(Space(context), fill())
            addView(primaryButton("홈으로", onHome))
        }
        twoColumns(left, courseMap().apply { show(course.path, emptyList()) })
    }
