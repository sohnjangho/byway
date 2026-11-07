package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course

/** 코스 데이터 출처와 앱 버전. */
fun aboutScreen(context: Context, courses: List<Course>, onBack: () -> Unit): View =
    with(context) {
        val edge = dp(Look.EDGE)
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val details = column().apply {
            addView(label("앱 정보", Look.TITLE, bold = true))
            addSpaced(label("코스 출처", Look.SMALL, Look.MUTED), 40)
            courses.forEach { course ->
                addSpaced(label(course.name, Look.BODY), 16)
                addSpaced(label(course.attribution, Look.SMALL, Look.MUTED), 4)
            }
            addSpaced(label("지도 데이터", Look.SMALL, Look.MUTED), 40)
            addSpaced(label("© OpenStreetMap contributors (ODbL)", Look.BODY), 16)
            addSpaced(label("앱 버전", Look.SMALL, Look.MUTED), 40)
            addSpaced(label(version.orEmpty(), Look.BODY), 16)
        }
        column().apply {
            setBackgroundColor(Look.BACKGROUND)
            setPadding(edge, edge, edge, edge)
            addView(
                quietButton("뒤로", onBack),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(ScrollView(context).apply { addView(details) }, fill().apply { topMargin = dp(32) })
        }
    }
