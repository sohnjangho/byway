package com.byway.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

internal const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
internal const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.label(value: CharSequence, size: Float, color: Int = palette.ink, bold: Boolean = false) =
    TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setLineSpacing(0f, 1.15f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        // 없으면 한글이 단어 한가운데서 줄을 바꾼다.
        if (Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    }

/** 글줄 수를 고정한다. 값이 바뀌어도 주행 화면의 배치가 움직이지 않게 한다. */
internal fun TextView.fixedLines(count: Int) = apply {
    setLines(count)
    ellipsize = TextUtils.TruncateAt.END
}

internal fun TextView.atMostLines(count: Int) = apply {
    maxLines = count
    ellipsize = TextUtils.TruncateAt.END
}

/** 값이 바뀌어도 자리가 흔들리지 않게 숫자 폭을 고정한다. */
internal fun TextView.tabular() = apply { fontFeatureSettings = "tnum" }

internal fun Context.rounded(color: Int, radius: Int = Dimens.RADIUS_MD, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(Dimens.HAIRLINE), it) }
    }

/** 누름 표시는 페이드나 물결 없이 색만 바꾼다. */
internal fun Context.pressable(
    normal: Int,
    pressed: Int,
    radius: Int = Dimens.RADIUS_MD,
    stroke: Int? = null,
): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed, radius, stroke))
    addState(intArrayOf(), rounded(normal, radius, stroke))
}

/** 커버처럼 그림이 면을 덮을 때의 누름 표시. 사진을 평소에 어둡게 하지 않으려고 누르는 동안에만 칠한다. */
internal fun Context.pressedVeil(): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(palette.accentSoft))
}

/**
 * 카드 면. 주간에는 그림자로 바닥에서 띄운다. 그림자는 면 바깥에 그려지므로 부모 안쪽 여백에 자리를 두고,
 * 부모가 그 여백에서 자르지 않게 한다. clipChildren 은 건드리지 않는다. 풀면 옆 ScrollView 내용이 칸 밖으로 넘친다.
 */
internal fun View.asCard(clickable: Boolean = false) = apply {
    val face = context.palette
    background = if (clickable) context.pressable(face.bgRaised, face.bgPressed) else context.rounded(face.bgRaised)
    if (clickable) pressMotion()
    elevation = context.dp(if (context.night) Dimens.CARD_SHADOW_NIGHT else Dimens.CARD_SHADOW_DAY).toFloat()
    post { (parent as? ViewGroup)?.clipToPadding = false }
}

private fun Context.button(title: String, size: Float, height: Int, onClick: () -> Unit) = Button(this).apply {
    text = title
    textSize = size
    isAllCaps = false
    pressMotion()
    minHeight = dp(height)
    setPadding(dp(Dimens.BUTTON_PADDING), 0, dp(Dimens.BUTTON_PADDING), 0)
    setOnClickListener { onClick() }
}

/** 화면의 다음 행동 하나를 맡는 큰 버튼. */
internal fun Context.primaryButton(title: String, onClick: () -> Unit): Button =
    button(title, Dimens.BODY, Dimens.BUTTON_LARGE, onClick).apply {
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(palette.onAccent)
        background = pressable(palette.accent, palette.accentPressed, Dimens.RADIUS_LG)
    }

internal fun Context.quietButton(title: String, onClick: () -> Unit): Button =
    button(title, Dimens.BODY, Dimens.BUTTON_MEDIUM, onClick).apply {
        setTextColor(palette.ink)
        background = pressable(palette.bgRaised, palette.bgPressed, Dimens.RADIUS_MD, palette.line)
    }

/** 주행 화면 버튼. 눈을 끄는 색면을 두지 않으려고 테두리만 그리고 눌림 움직임도 뺀다. */
internal fun Context.outlineButton(title: String, onClick: () -> Unit): Button =
    button(title, Dimens.BODY, Dimens.BUTTON_MEDIUM, onClick).apply {
        stateListAnimator = null
        setTextColor(palette.ink)
        background = pressable(Color.TRANSPARENT, palette.bgPressed, Dimens.RADIUS_MD, palette.line)
    }

/** 그려지는 높이는 64dp 지만 터치 영역은 76dp 를 지키도록 위아래에 투명한 여백을 둔다. */
internal fun Context.smallButton(title: String, onClick: () -> Unit): Button =
    button(title, Dimens.SMALL, Dimens.TOUCH_MIN, onClick).apply {
        setTextColor(palette.ink)
        val inset = dp((Dimens.TOUCH_MIN - Dimens.BUTTON_SMALL) / 2)
        val face = pressable(palette.bgRaised, palette.bgPressed, Dimens.RADIUS_SM, palette.line)
        background = InsetDrawable(face, 0, inset, 0, inset)
        // InsetDrawable 은 자기 여백을 안쪽 여백으로 덮어쓴다.
        setPadding(dp(Dimens.SPACE_6), 0, dp(Dimens.SPACE_6), 0)
    }

internal fun Context.column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

internal fun Context.row(): LinearLayout = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

internal fun LinearLayout.addSpaced(view: View, top: Int) {
    addView(view, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(top) })
}

/** 글과 버튼 칸, 그림 칸을 나눈다. 넓으면 그림이 오른쪽에, 좁으면 위에 온다. */
internal fun Context.panes(text: View, picture: View, wide: Boolean): LinearLayout =
    if (wide) {
        row().apply {
            gravity = Gravity.NO_GRAVITY
            addView(text, LinearLayout.LayoutParams(dp(Dimens.TEXT_PANE_WIDTH), MATCH).apply { marginEnd = dp(Dimens.SPACE_10) })
            addView(picture, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    } else {
        column().apply {
            addView(picture, LinearLayout.LayoutParams(MATCH, dp(Dimens.SUMMARY_PICTURE_HEIGHT)))
            addView(text, fill().apply { topMargin = dp(Dimens.SPACE_4) })
        }
    }

/** 남는 세로 공간을 차지해 그 아래 버튼을 화면 바닥에 붙인다. */
internal fun fill() = LinearLayout.LayoutParams(MATCH, 0, 1f)

internal fun matchFrame() = FrameLayout.LayoutParams(MATCH, MATCH)
