package com.byway.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space

/**
 * 메뉴를 붙인다. 전체와 2/3 창은 왼쪽, 1/3 창은 위쪽이다.
 * [over]면 메뉴가 콘텐츠 위에 반투명으로 얹히고 콘텐츠는 메뉴 자리까지 채운다.
 */
internal fun Context.withRail(selected: Tab, window: WindowClass, onTab: (Tab) -> Unit, content: View, over: Boolean = true): View =
    FrameLayout(this).apply {
        setBackgroundColor(palette.bg)
        val top = window == WindowClass.THIRD
        val rail = rail(selected, top, over, onTab)
        val inset = FrameLayout.LayoutParams(MATCH, MATCH).apply {
            if (!over && top) topMargin = dp(Dimens.RAIL_TOP_HEIGHT)
            if (!over && !top) leftMargin = dp(Dimens.RAIL_WIDTH)
        }
        addView(content, inset)
        val size = if (top) {
            FrameLayout.LayoutParams(MATCH, dp(Dimens.RAIL_TOP_HEIGHT))
        } else {
            FrameLayout.LayoutParams(dp(Dimens.RAIL_WIDTH), MATCH)
        }
        addView(rail, size)
    }

private fun Context.rail(selected: Tab, top: Boolean, over: Boolean, onTab: (Tab) -> Unit): View {
    val body = if (top) row() else column()
    return body.apply {
        setBackgroundColor(if (over) palette.softWash else palette.bgRaised)
        // 메뉴 뒤로 터치가 지나가지 않게 한다.
        isClickable = true
        if (top) {
            setPadding(dp(Dimens.SPACE_6), dp(Dimens.SPACE_2), dp(Dimens.SPACE_6), dp(Dimens.SPACE_2))
        } else {
            setPadding(dp(Dimens.SPACE_3), dp(Dimens.SPACE_6), dp(Dimens.SPACE_3), dp(Dimens.SPACE_6))
        }
        val brand = label(applicationInfo.loadLabel(packageManager), Dimens.BRAND, palette.accent).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = dp(Dimens.TOUCH_MIN)
            setOnClickListener { onTab(Tab.HOME) }
        }
        val brandSize = if (top) {
            LinearLayout.LayoutParams(WRAP, MATCH).apply { marginEnd = dp(Dimens.SPACE_4) }
        } else {
            LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(Dimens.SPACE_6) }
        }
        addView(brand, brandSize)
        Tab.entries.forEach { tab ->
            val size = if (top) {
                LinearLayout.LayoutParams(WRAP, dp(Dimens.TOUCH_MIN))
            } else {
                LinearLayout.LayoutParams(MATCH, dp(Dimens.RAIL_ITEM_HEIGHT)).apply { bottomMargin = dp(Dimens.SPACE_2) }
            }
            addView(railItem(tab, tab == selected, top, over, onTab), size)
        }
        addView(Space(context), if (top) LinearLayout.LayoutParams(0, 0, 1f) else LinearLayout.LayoutParams(0, 0, 1f))
        addView(avatar(selected == Tab.MINE, onTab), LinearLayout.LayoutParams(if (top) WRAP else MATCH, WRAP))
    }
}

private fun Context.railItem(tab: Tab, on: Boolean, top: Boolean, over: Boolean, onTab: (Tab) -> Unit): View {
    val colour = if (on || over) palette.ink else palette.inkSecondary
    return label(tab.title, Dimens.SMALL, colour, bold = on).apply {
        gravity = Gravity.CENTER
        if (top) setPadding(dp(Dimens.SPACE_4), 0, dp(Dimens.SPACE_4), 0)
        background = if (on) rounded(palette.accentSoft) else pressable(android.graphics.Color.TRANSPARENT, palette.bgPressed)
        setOnClickListener { onTab(tab) }
    }
}

private fun Context.avatar(on: Boolean, onTab: (Tab) -> Unit): View = FrameLayout(this).apply {
    minimumHeight = dp(Dimens.TOUCH_MIN)
    setOnClickListener { onTab(Tab.MINE) }
    val mark = label("나", Dimens.SMALL, if (on) palette.onAccent else palette.accent, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(if (on) palette.accent else palette.bgPressed, Dimens.AVATAR_SMALL / 2)
    }
    addView(mark, FrameLayout.LayoutParams(dp(Dimens.AVATAR_SMALL), dp(Dimens.AVATAR_SMALL), Gravity.CENTER))
}
