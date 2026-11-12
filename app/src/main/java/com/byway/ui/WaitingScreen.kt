package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View

/** 달리는 코스 없이 주행 중일 때. 문구 두 줄뿐이고 누를 곳이 없다. */
fun waitingScreen(context: Context): View = with(context) {
    column().apply {
        gravity = Gravity.CENTER
        setBackgroundColor(palette.bg)
        val edge = dp(Dimens.SPACE_10)
        setPadding(edge, edge, edge, edge)
        addSpaced(label("정차하면 코스를 고를 수 있습니다", Dimens.DRIVE_SECTION, bold = true).apply { gravity = Gravity.CENTER }, 0)
        addSpaced(label("기어를 P에 두면 코스 목록이 열립니다", Dimens.DRIVE_LINE, palette.inkSecondary).apply { gravity = Gravity.CENTER }, Dimens.SPACE_4)
    }
}
