package com.byway.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space

/** 상단 고정 메뉴를 붙인다. 정차 화면은 모두 이 틀 안에 들어간다. */
internal fun Context.withTabBar(selected: Tab, wide: Boolean, onTab: (Tab) -> Unit, content: View): View =
    column().apply {
        setBackgroundColor(palette.bg)
        addView(tabBar(selected, wide, onTab), LinearLayout.LayoutParams(MATCH, dp(Dimens.TAB_BAR_HEIGHT)))
        addView(View(context).apply { setBackgroundColor(palette.line) }, LinearLayout.LayoutParams(MATCH, dp(Dimens.HAIRLINE)))
        addView(content, fill())
    }

private fun Context.tabBar(selected: Tab, wide: Boolean, onTab: (Tab) -> Unit): View = row().apply {
    val edge = dp(if (wide) Dimens.SPACE_10 else Dimens.SPACE_6)
    setPadding(edge, 0, edge, 0)
    val brand = label(applicationInfo.loadLabel(packageManager), Dimens.BRAND, palette.accent).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    addView(brand, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(if (wide) Dimens.SPACE_10 else Dimens.SPACE_3) })
    Tab.entries.forEach { tab ->
        val padding = dp(if (wide) Dimens.SPACE_6 else Dimens.SPACE_4)
        addView(tabButton(tab, tab == selected, padding, onTab), LinearLayout.LayoutParams(WRAP, MATCH))
    }
    addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
    addView(meButton(selected == Tab.MINE, onTab))
}

private fun Context.tabButton(tab: Tab, on: Boolean, padding: Int, onTab: (Tab) -> Unit): View =
    label(tab.title, Dimens.BODY, if (on) palette.accent else palette.inkMuted, bold = on).apply {
        gravity = Gravity.CENTER
        if (on) {
            val inset = dp((Dimens.TAB_BAR_HEIGHT - Dimens.TAB_PILL_HEIGHT) / 2)
            background = InsetDrawable(rounded(palette.accentSoft, Dimens.RADIUS_SM), 0, inset, 0, inset)
        }
        // InsetDrawable 은 자기 여백을 안쪽 여백으로 덮어쓴다.
        setPadding(padding, 0, padding, 0)
        setOnClickListener { onTab(tab) }
    }

private fun Context.meButton(on: Boolean, onTab: (Tab) -> Unit): View = row().apply {
    minimumHeight = dp(Dimens.TOUCH_MIN)
    setOnClickListener { onTab(Tab.MINE) }
    val mark = label("나", Dimens.SMALL, if (on) palette.onAccent else palette.accent, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(if (on) palette.accent else palette.bgPressed, Dimens.AVATAR_SMALL / 2)
    }
    addView(
        mark,
        LinearLayout.LayoutParams(dp(Dimens.AVATAR_SMALL), dp(Dimens.AVATAR_SMALL)).apply { marginEnd = dp(Dimens.SPACE_3) },
    )
    addView(label("내 정보", Dimens.SMALL))
}
