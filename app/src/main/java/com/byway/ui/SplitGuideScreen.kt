package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import com.byway.core.course.Course

/**
 * 출발 화면 고르기(PM 11-11 "어떻게 구성되는지 전혀 모르겠음"). 플레오스 화면을 간략히 그린 그림 세 개 중에서 고른다.
 * 고른 배치가 지금 창에 맞으면 바로 출발하고, 아니면 어느 쪽으로 끌지 안내하며 기다린다([dragHint]). 정차 중에만 보인다.
 * [chosen]은 이미 고르고 화면 나눔을 기다리는 배치다.
 */
internal fun splitGuideScreen(
    context: Context,
    kit: BrowseKit,
    course: Course?,
    chosen: DriveLayout?,
    onChoose: (DriveLayout) -> Unit,
    onClose: () -> Unit,
): View = with(context) {
    val third = kit.window == WindowClass.THIRD
    var picked = chosen ?: currentDriveLayout()
    val cards = mutableMapOf<DriveLayout, View>()
    fun mark() = cards.forEach { (layout, card) -> card.background = choiceFace(layout == picked) }
    val body = column().apply {
        val edge = dp(kit.edge)
        setPadding(edge, edge, edge, edge)
        addView(label("코스 출발", Dimens.SMALL, palette.inkSecondary))
        addSpaced(label(START_CHOICE_TITLE, Dimens.HEADING, bold = true), Dimens.SPACE_3)
        val list = if (third) column() else row().apply { gravity = Gravity.TOP }
        DriveLayout.entries.forEachIndexed { i, layout ->
            val card = choiceCard(layout, third) {
                picked = layout
                mark()
            }
            cards[layout] = card
            val size = if (third) {
                LinearLayout.LayoutParams(MATCH, WRAP).apply { if (i > 0) topMargin = dp(Dimens.SPACE_3) }
            } else {
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(Dimens.SPACE_4) }
            }
            list.addView(card, size)
        }
        addSpaced(list, Dimens.SPACE_6)
        mark()
        // 고른 뒤 화면 나눔을 바꿔야 하면 어느 쪽으로 끌지 크게 알린다. 바뀌는 순간 출발한다.
        chosen?.let { dragHint(it, kit.window) }?.let { hint ->
            addSpaced(label(hint, Dimens.BODY, palette.accent, bold = true), Dimens.SPACE_6)
        }
        addView(Space(context), fill())
        val buttons = LinearLayout(context).apply {
            addView(primaryButton(START_CHOICE_BUTTON) { onChoose(picked) }, LinearLayout.LayoutParams(0, WRAP, 1.4f).apply { marginEnd = dp(Dimens.SPACE_4) })
            addView(quietButton(START_CHOICE_CLOSE, onClose), LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        addSpaced(buttons, Dimens.SPACE_6)
    }
    // 출발할 코스의 분위기를 깐다(화면 다듬기 설계 4절).
    MoodLayer(context, kit.window).hold(body).apply { show(course) }
}

private fun Context.choiceFace(on: Boolean) =
    if (on) rounded(palette.bgRaised, Dimens.RADIUS_MD, palette.accent) else rounded(palette.bgRaised, Dimens.RADIUS_MD, palette.lineSubtle)

/** 고르기 칸 하나: 플레오스 화면 그림과 이름. 1/3 창은 그림을 왼쪽, 이름을 오른쪽에 둔다. */
private fun Context.choiceCard(layout: DriveLayout, third: Boolean, onPick: () -> Unit): View {
    val (kind, panes) = layout.title.split(" · ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val text = column().apply {
        addView(label(kind, Dimens.SMALL, bold = true))
        addView(label(panes, Dimens.LABEL, palette.inkSecondary).atMostLines(2))
        addSpaced(label(choiceNote(layout), Dimens.LABEL).atMostLines(3), Dimens.SPACE_2)
    }
    val picture = pleosScreen(layout)
    val pad = dp(Dimens.SPACE_4)
    return (if (third) row() else column()).apply {
        setPadding(pad, pad, pad, pad)
        isClickable = true
        pressMotion()
        setOnClickListener { onPick() }
        if (third) {
            addView(picture, LinearLayout.LayoutParams(dp(PICTURE_WIDTH_THIRD), dp(PICTURE_WIDTH_THIRD * 9 / 16)))
            addView(text, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(Dimens.SPACE_4) })
        } else {
            addView(picture, LinearLayout.LayoutParams(MATCH, dp(PICTURE_HEIGHT)))
            addSpaced(text, Dimens.SPACE_3)
        }
    }
}

/**
 * 플레오스 화면을 간략히 그린 그림. 위 상태 막대, 아래 버튼 줄, 그 사이에 칸을 실제 비율(차량 정보 853 : 앱 칸 1682,
 * 3분할은 853 : 841 : 841)로 나눈다. 에움길 칸은 강조색, 내비 칸은 지도처럼 어두운 면이다.
 */
private fun Context.pleosScreen(layout: DriveLayout): View = column().apply {
    background = rounded(palette.photoScrim, Dimens.RADIUS_SM)
    val gap = dp(2)
    setPadding(gap, gap, gap, gap)
    addView(View(context).apply { background = rounded(palette.line, 2) }, LinearLayout.LayoutParams(MATCH, dp(5)))
    val panes = row().apply { gravity = Gravity.CENTER_VERTICAL }
    layout.panes.forEachIndexed { i, name ->
        val (fill, ink) = when (name) {
            "에움길" -> palette.accent to palette.onAccent
            "내비" -> palette.inkSecondary to palette.bg
            else -> palette.bgRaised to palette.inkSecondary
        }
        val weight = when {
            i == 0 -> 853f
            layout.panes.size == 2 -> 1682f
            else -> 841f
        }
        panes.addView(label(name.replace(" ", "\n"), Dimens.LABEL, ink, bold = name != "차량 정보").apply {
            gravity = Gravity.CENTER
            background = rounded(fill, 3)
        }, LinearLayout.LayoutParams(0, MATCH, weight).apply { if (i > 0) marginStart = gap })
    }
    addView(panes, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = gap; bottomMargin = gap })
    addView(View(context).apply { background = rounded(palette.line, 2) }, LinearLayout.LayoutParams(MATCH, dp(8)))
}

/** 고르기 칸마다 그 배치에서 칸이 하는 일. */
internal fun choiceNote(layout: DriveLayout): String = when (layout) {
    DriveLayout.SPLIT -> "내비가 가운데에서 길을 안내하고, 에움길은 오른쪽에서 남은 거리와 코스 주변을 보여 줍니다"
    DriveLayout.SPLIT_BYWAY -> "에움길이 가운데에서 남은 거리와 코스 주변을 보여 주고, 내비는 오른쪽에서 길을 안내합니다"
    DriveLayout.NAVI -> "내비가 넓게 길을 안내합니다. 에움길은 뒤에서 코스를 이어 가며 음성으로 알립니다"
}

private const val PICTURE_HEIGHT = 190
private const val PICTURE_WIDTH_THIRD = 200
