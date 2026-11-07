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
            addView(label("앱 정보", Dimens.TITLE, bold = true))
            addSpaced(label("출처", Dimens.SMALL, palette.inkMuted), Dimens.SPACE_8)
            credits(kit.book.courses).forEach { addSpaced(label(it, Dimens.BODY), Dimens.SPACE_2) }
            addSpaced(label("앱 버전", Dimens.SMALL, palette.inkMuted), Dimens.SPACE_8)
            addSpaced(label(version.orEmpty(), Dimens.BODY), Dimens.SPACE_2)
        }
        column().apply {
            setBackgroundColor(palette.bg)
            setPadding(edge, dp(Dimens.SPACE_6), edge, dp(Dimens.SPACE_6))
            addView(smallButton("← 뒤로", onBack), LinearLayout.LayoutParams(WRAP, WRAP))
            addView(ScrollView(context).apply { addView(details) }, fill().apply { topMargin = dp(Dimens.SPACE_4) })
        }
    }

private fun credits(courses: List<Course>): List<String> =
    (courses.map { it.attribution } + courses.map { it.sceneryCredit } + FIXED_CREDITS).filter { it.isNotBlank() }.distinct()

private val FIXED_CREDITS = listOf(
    "© OpenStreetMap contributors (ODbL)",
    "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)",
    "고도: Open-Meteo, Copernicus DEM",
)
