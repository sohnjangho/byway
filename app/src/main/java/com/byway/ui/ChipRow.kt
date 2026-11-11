package com.byway.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** 칩 하나의 값과 문구. */
internal class Choice(val key: String, val title: String)

/**
 * 하나만 고르는 칩 줄. 고른 표시는 칩 안에서 바꾸고 [onPick]은 아래 내용만 바꾼다.
 * 줄 전체를 다시 만들면 옆으로 넘겨 둔 위치가 사라진다.
 */
internal fun Context.chipRow(choices: List<Choice>, selected: String?, size: Float, onPick: (String) -> Unit): View =
    HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        val chips = mutableMapOf<String, TextView>()
        val line = LinearLayout(context)
        fun mark(key: String?) = chips.forEach { (chipKey, chip) -> paint(chip, chipKey == key) }
        choices.forEachIndexed { i, choice ->
            val chip = label(choice.title, size).apply {
                gravity = Gravity.CENTER
                minHeight = dp(Dimens.TOUCH_MIN)
                setPadding(dp(Dimens.SPACE_6), 0, dp(Dimens.SPACE_6), 0)
                pressMotion()
                setOnClickListener {
                    mark(choice.key)
                    onPick(choice.key)
                }
            }
            chips[choice.key] = chip
            line.addView(
                chip,
                LinearLayout.LayoutParams(WRAP, dp(Dimens.TOUCH_MIN)).apply { if (i > 0) marginStart = dp(Dimens.SPACE_3) },
            )
        }
        mark(selected)
        addView(line)
    }.bounceAtEnds(vertical = false)

/** 고른 칩은 accent 면이 아니라 옅은 바탕이다. 한 화면의 accent 면은 주 버튼 하나다. */
internal fun Context.paint(chip: TextView, on: Boolean) {
    chip.setTextColor(if (on) palette.accent else palette.ink)
    chip.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    chip.background = if (on) {
        pressable(palette.accentSoft, palette.bgPressed, Dimens.RADIUS_SM)
    } else {
        pressable(palette.bgRaised, palette.bgPressed, Dimens.RADIUS_SM, palette.lineSubtle)
    }
}
