package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** 코스·지도·위성·고도 자료 출처와 앱 버전. */
internal fun aboutScreen(context: Context, kit: BrowseKit, onBack: () -> Unit): View =
    with(context) {
        val edge = dp(kit.edge)
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val details = column().apply {
            addView(label("앱 정보", Dimens.TITLE, bold = true))
            addSpaced(label("출처", Dimens.SMALL, palette.inkMuted), Dimens.SPACE_8)
            creditLines(kit.book.courses).forEach { addSpaced(label(it, Dimens.BODY), Dimens.SPACE_2) }
            addSpaced(label("오픈소스", Dimens.SMALL, palette.inkMuted), Dimens.SPACE_8)
            OPEN_SOURCE_NOTICES.forEach { addSpaced(label(it, Dimens.BODY), Dimens.SPACE_2) }
            addSpaced(label("위치 정보", Dimens.SMALL, palette.inkMuted), Dimens.SPACE_8)
            addSpaced(label("위치는 코스 안내에만 쓰고 저장하거나 전송하지 않습니다.", Dimens.BODY), Dimens.SPACE_2)
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
