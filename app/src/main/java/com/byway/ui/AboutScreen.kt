package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course

/** 코스·지도·위성·고도 자료 출처와 앱 버전. */
internal fun aboutScreen(context: Context, kit: BrowseKit, onBack: () -> Unit): View =
    with(context) {
        val edge = dp(kit.edge)
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val details = column().apply {
            addView(label("앱 정보", Look.TITLE, bold = true))
            addSpaced(label("출처", Look.SMALL, Look.MUTED), Look.WIDE_GAP)
            credits(kit.book.courses).forEach { addSpaced(label(it, Look.BODY), 8) }
            addSpaced(label("앱 버전", Look.SMALL, Look.MUTED), Look.WIDE_GAP)
            addSpaced(label(version.orEmpty(), Look.BODY), 8)
        }
        column().apply {
            setBackgroundColor(Look.BACKGROUND)
            setPadding(edge, dp(Look.GAP), edge, dp(Look.GAP))
            addView(smallButton("← 뒤로", onBack), LinearLayout.LayoutParams(WRAP, WRAP))
            addView(ScrollView(context).apply { addView(details) }, fill().apply { topMargin = dp(Look.CARD_GAP) })
        }
    }

private fun credits(courses: List<Course>): List<String> =
    (courses.map { it.attribution } + FIXED_CREDITS).filter { it.isNotBlank() }.distinct()

private val FIXED_CREDITS = listOf(
    "© OpenStreetMap contributors (ODbL)",
    "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)",
    "고도: Open-Meteo, Copernicus DEM",
)
