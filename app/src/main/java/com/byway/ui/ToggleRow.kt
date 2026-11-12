package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/** 켜고 끄는 줄. 움직임 없이 색과 위치만 바뀐다. */
internal fun Context.toggleRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View = row().apply {
    var on = initial
    minimumHeight = dp(Dimens.TOUCH_MIN)
    background = rounded(palette.bgDeep, Dimens.RADIUS_SM)
    setPadding(dp(Dimens.SPACE_4), 0, dp(Dimens.SPACE_3), 0)
    val knob = View(context)
    val pill = FrameLayout(context)
    fun paint() {
        pill.background = rounded(if (on) palette.accent else palette.bgPressed, Dimens.TOGGLE_HEIGHT / 2)
        knob.background = rounded(if (on) palette.onAccent else palette.inkMuted, Dimens.TOGGLE_KNOB / 2)
        val side = if (on) Gravity.END else Gravity.START
        knob.layoutParams = FrameLayout.LayoutParams(dp(Dimens.TOGGLE_KNOB), dp(Dimens.TOGGLE_KNOB), side or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(Dimens.TOGGLE_INSET)
            marginEnd = dp(Dimens.TOGGLE_INSET)
        }
    }
    pill.addView(knob)
    paint()
    addView(label(title, Dimens.LABEL).atMostLines(1), LinearLayout.LayoutParams(0, WRAP, 1f))
    addView(pill, LinearLayout.LayoutParams(dp(Dimens.TOGGLE_WIDTH), dp(Dimens.TOGGLE_HEIGHT)))
    contentDescription = title
    pressMotion()
    setOnClickListener {
        on = !on
        onChange(on)
        paint()
    }
}
