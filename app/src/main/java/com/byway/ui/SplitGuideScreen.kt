package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space

/**
 * 3분할로 출발하려면 화면을 나누라는 안내(주행 화면 3분할 설계 2절). 정차 중에만 보인다.
 * 앱은 화면을 직접 나눌 수 없어서, 사용자가 오른쪽 끝 ⋮을 끌어 에움길 창이 1/3이 되면 [MainActivity]가 바로 출발한다.
 * 위에는 나뉜 모습(차량 정보, 내비, 에움길)을 그린다. 에움길 칸만 옅은 강조색이다.
 */
internal fun splitGuideScreen(context: Context, kit: BrowseKit, onPlain: () -> Unit, onClose: () -> Unit): View = with(context) {
    column().apply {
        setBackgroundColor(palette.bg)
        val edge = dp(kit.edge)
        setPadding(edge, edge, edge, edge)
        addView(label("코스 출발", Dimens.SMALL, palette.inkSecondary))
        addSpaced(label(SPLIT_GUIDE_TITLE, Dimens.HEADING, bold = true), Dimens.SPACE_3)
        addSpaced(splitPicture(), Dimens.SPACE_6)
        addSpaced(label(SPLIT_GUIDE_BODY, Dimens.BODY), Dimens.SPACE_6)
        addSpaced(label(DRIVE_LAYOUT_NOTE, Dimens.LABEL, palette.inkSecondary), Dimens.SPACE_2)
        addView(Space(context), fill())
        val buttons = LinearLayout(context).apply {
            addView(quietButton(SPLIT_GUIDE_PLAIN, onPlain), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_4) })
            addView(quietButton(SPLIT_GUIDE_CLOSE, onClose), LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        addSpaced(buttons, Dimens.SPACE_6)
    }
}

/** 나뉜 화면 그림. 칸 폭은 실제 비율(853 : 841 : 841)에 가깝게 같은 너비로 둔다. */
private fun Context.splitPicture(): View = row().apply {
    val pane = { title: String, color: Int, ink: Int ->
        label(title, Dimens.SMALL, ink, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(color, Dimens.RADIUS_SM, palette.lineSubtle)
        }
    }
    val height = dp(Dimens.TOUCH_MIN * 2)
    addView(pane("차량 정보", palette.bgRaised, palette.inkSecondary), LinearLayout.LayoutParams(0, height, 1f))
    addView(pane("내비", palette.bgRaised, palette.ink), LinearLayout.LayoutParams(0, height, 1f).apply { marginStart = dp(Dimens.SPACE_2) })
    addView(pane("에움길", palette.accentSoft, palette.accent), LinearLayout.LayoutParams(0, height, 1f).apply { marginStart = dp(Dimens.SPACE_2) })
    addView(label("⋮", Dimens.HEADING, palette.accent, bold = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(WRAP, height).apply { marginStart = dp(Dimens.SPACE_2) })
}
