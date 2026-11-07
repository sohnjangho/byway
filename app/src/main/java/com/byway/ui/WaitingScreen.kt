package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View

/** 달리는 코스 없이 주행 중일 때. 글 한 줄뿐이고 누를 곳이 없다. */
fun waitingScreen(context: Context): View =
    context.label("정차하면 코스를 고를 수 있습니다", Look.HEADING).apply {
        gravity = Gravity.CENTER
        setBackgroundColor(Look.BACKGROUND)
    }
