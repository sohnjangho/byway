package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course

/** 정차 중 첫 화면. 코스를 고르는 곳이다. */
fun homeScreen(context: Context, courses: List<Course>, onCourse: (Course) -> Unit, onAbout: () -> Unit): View =
    with(context) {
        val edge = dp(Look.EDGE)
        val list = column().apply { courses.forEach { addSpaced(courseCard(it) { onCourse(it) }, Look.GAP) } }
        column().apply {
            setBackgroundColor(Look.BACKGROUND)
            setPadding(edge, edge, edge, 0)
            addView(header(onAbout))
            val scroll = ScrollView(context).apply {
                addView(list)
                clipToPadding = false
                setPadding(0, 0, 0, edge)
            }
            addView(scroll, fill().apply { topMargin = dp(16) })
        }
    }

private fun Context.header(onAbout: () -> Unit) = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    val title = column().apply {
        addView(label(applicationInfo.loadLabel(packageManager), Look.TITLE, Look.ACCENT, bold = true))
        addSpaced(label("최단 경로가 아니라, 그 길을 달리게 합니다", Look.BODY, Look.MUTED), 8)
    }
    addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    addView(quietButton("앱 정보", onAbout))
}

private fun Context.courseCard(course: Course, onClick: () -> Unit) = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    background = pressable(Look.SURFACE, Look.SURFACE_PRESSED)
    setPadding(dp(40), dp(32), dp(32), dp(32))
    setOnClickListener { onClick() }

    val text = column().apply {
        addView(label(course.name, Look.HEADING, bold = true))
        if (course.summary.isNotBlank()) addSpaced(label(course.summary, Look.BODY, Look.MUTED).atMostLines(2), 8)
        addSpaced(label(statsText(course), Look.BODY), 12)
        tagRow(tagLabels(course.tags))?.let { addSpaced(it, 16) }
    }
    addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    val thumbnail = CoursePathView(context).apply { show(course.path, emptyList()) }
    addView(thumbnail, LinearLayout.LayoutParams(dp(340), dp(220)).apply { marginStart = dp(32) })
}

private fun Context.tagRow(tags: List<String>): View? {
    if (tags.isEmpty()) return null
    return LinearLayout(this).apply {
        tags.forEach { tag ->
            addView(
                chip(tag),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(12)
                },
            )
        }
    }
}
