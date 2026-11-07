package com.byway.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space

private const val BAR_HEIGHT = 104

/** 상단 고정 메뉴를 붙인다. 정차 화면은 모두 이 틀 안에 들어간다. */
internal fun Context.withTabBar(selected: Tab, wide: Boolean, onTab: (Tab) -> Unit, content: View): View =
    column().apply {
        setBackgroundColor(Look.BACKGROUND)
        addView(tabBar(selected, wide, onTab), LinearLayout.LayoutParams(MATCH, dp(BAR_HEIGHT)))
        addView(
            View(context).apply { setBackgroundColor(Look.SURFACE_PRESSED) },
            LinearLayout.LayoutParams(MATCH, dp(1)),
        )
        addView(content, fill())
    }

private fun Context.tabBar(selected: Tab, wide: Boolean, onTab: (Tab) -> Unit): View = row().apply {
    val edge = dp(if (wide) Look.EDGE else Look.NARROW_EDGE)
    setPadding(edge, 0, edge, 0)
    val brand = label(applicationInfo.loadLabel(packageManager), 40f, Look.ACCENT).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    addView(brand, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(if (wide) 48 else 12) })
    Tab.entries.forEach { tab ->
        addView(tabButton(tab, tab == selected, dp(if (wide) 24 else 16), onTab), LinearLayout.LayoutParams(WRAP, MATCH))
    }
    addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
    addView(meButton(selected == Tab.MINE, onTab))
}

private fun Context.tabButton(tab: Tab, on: Boolean, padding: Int, onTab: (Tab) -> Unit): View =
    label(tab.title, Look.BODY, if (on) Look.INK else Look.MUTED, bold = on).apply {
        gravity = Gravity.CENTER
        setPadding(padding, 0, padding, 0)
        if (on) background = underline()
        setOnClickListener { onTab(tab) }
    }

private fun Context.underline() = LayerDrawable(arrayOf(ColorDrawable(Look.ACCENT))).apply {
    setLayerGravity(0, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
    setLayerHeight(0, dp(4))
}

private fun Context.meButton(on: Boolean, onTab: (Tab) -> Unit): View = row().apply {
    minimumHeight = dp(Look.TOUCH_MIN)
    setOnClickListener { onTab(Tab.MINE) }
    val mark = label("나", Look.SMALL, if (on) Look.ON_ACCENT else Look.ACCENT, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(if (on) Look.ACCENT else Look.SURFACE_PRESSED, 30)
    }
    addView(mark, LinearLayout.LayoutParams(dp(60), dp(60)).apply { marginEnd = dp(12) })
    addView(label("내 정보", Look.SMALL))
}
