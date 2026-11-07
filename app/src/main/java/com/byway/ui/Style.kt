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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** 화면들이 함께 쓰는 색과 크기. Pleos 공식 Figma 에서 나온 디자인 토큰 값이다. */
internal object Look {
    val BACKGROUND = Color.parseColor("#131417")
    val SURFACE = Color.parseColor("#1D1E21")
    val SURFACE_PRESSED = Color.parseColor("#313236")
    val LINE = Color.parseColor("#44464E")
    val INK = Color.parseColor("#F7F8FA")
    val MUTED = Color.parseColor("#A4A8AE")
    val ACCENT = Color.parseColor("#E8A15C")
    val ACCENT_PRESSED = Color.parseColor("#C98744")
    val ON_ACCENT = Color.parseColor("#231509")
    val COVER_BAND = Color.parseColor("#E6131417")
    val PRESSED_VEIL = Color.parseColor("#66131417")

    val SECTION_FOREST = Color.parseColor("#7CC48F")
    val SECTION_CITY = Color.parseColor("#A3B3D1")
    val SECTION_RIVER = Color.parseColor("#5FC6D8")
    val AIR_FOREST = Color.parseColor("#121F1C")
    val AIR_CITY = Color.parseColor("#141B26")
    val AIR_RIVER = Color.parseColor("#10212A")

    const val TITLE = 48f
    const val VALUE = 40f
    const val HEADING = 36f
    const val BODY = 30f
    const val SMALL = 26f
    const val LABEL = 24f
    const val DRIVE_NUMBER = 112f
    const val DRIVE_SECTION = 40f
    const val DRIVE_UNIT = 36f
    const val DRIVE_LINE = 32f

    const val EDGE = 40
    const val NARROW_EDGE = 24
    const val GAP = 24
    const val WIDE_GAP = 32
    const val CARD_GAP = 16

    const val RADIUS_SM = 16
    const val RADIUS_MD = 24
    const val RADIUS_LG = 32

    const val TOUCH_MIN = 76
    const val BUTTON_LARGE = 100
    const val BUTTON_MEDIUM = 80
    const val BUTTON_SMALL = 64
}

internal const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
internal const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

/** 구간 분위기별 색. 코스 선, 구간 점, 구간 막대, 주행 화면 구간 이름이 같은 색을 쓴다. */
internal fun moodColor(mood: String?): Int = when (mood) {
    "forest" -> Look.SECTION_FOREST
    "river" -> Look.SECTION_RIVER
    else -> Look.SECTION_CITY
}

/** 구간 분위기별 주행 화면 배경. */
internal fun airColor(mood: String?): Int = when (mood) {
    "forest" -> Look.AIR_FOREST
    "river" -> Look.AIR_RIVER
    else -> Look.AIR_CITY
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.label(value: CharSequence, size: Float, color: Int = Look.INK, bold: Boolean = false) =
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

internal fun Context.rounded(color: Int, radius: Int = Look.RADIUS_MD, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

/** 누름 표시는 페이드나 물결 없이 색만 바꾼다. 주행 화면에 움직임을 만들지 않기 위해서다. */
internal fun Context.pressable(
    normal: Int,
    pressed: Int,
    radius: Int = Look.RADIUS_MD,
    stroke: Int? = null,
): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed, radius, stroke))
    addState(intArrayOf(), rounded(normal, radius, stroke))
}

/** 커버처럼 그림이 면을 덮을 때의 누름 표시. */
internal fun pressedVeil(): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Look.PRESSED_VEIL))
}

private fun Context.button(title: String, size: Float, height: Int, onClick: () -> Unit) = Button(this).apply {
    text = title
    textSize = size
    isAllCaps = false
    stateListAnimator = null
    minHeight = dp(height)
    setPadding(dp(32), 0, dp(32), 0)
    setOnClickListener { onClick() }
}

/** 화면의 다음 행동 하나를 맡는 큰 버튼. */
internal fun Context.primaryButton(title: String, onClick: () -> Unit): Button =
    button(title, Look.BODY, Look.BUTTON_LARGE, onClick).apply {
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Look.ON_ACCENT)
        background = pressable(Look.ACCENT, Look.ACCENT_PRESSED, Look.RADIUS_LG)
    }

internal fun Context.quietButton(title: String, onClick: () -> Unit): Button =
    button(title, Look.BODY, Look.BUTTON_MEDIUM, onClick).apply {
        setTextColor(Look.INK)
        background = pressable(Look.SURFACE, Look.SURFACE_PRESSED, Look.RADIUS_MD, Look.LINE)
    }

/** 주행 화면 버튼. 눈을 끄는 색면을 두지 않으려고 테두리만 그린다. */
internal fun Context.outlineButton(title: String, onClick: () -> Unit): Button =
    button(title, Look.BODY, Look.BUTTON_MEDIUM, onClick).apply {
        setTextColor(Look.INK)
        background = pressable(Color.TRANSPARENT, Look.SURFACE_PRESSED, Look.RADIUS_MD, Look.LINE)
    }

/** 그려지는 높이는 64dp 지만 터치 영역은 76dp 를 지키도록 위아래에 투명한 여백을 둔다. */
internal fun Context.smallButton(title: String, onClick: () -> Unit): Button =
    button(title, Look.SMALL, Look.TOUCH_MIN, onClick).apply {
        setTextColor(Look.INK)
        val inset = dp((Look.TOUCH_MIN - Look.BUTTON_SMALL) / 2)
        background = InsetDrawable(pressable(Look.SURFACE, Look.SURFACE_PRESSED, Look.RADIUS_SM, Look.LINE), 0, inset, 0, inset)
        // InsetDrawable 은 자기 여백을 안쪽 여백으로 덮어쓴다.
        setPadding(dp(24), 0, dp(24), 0)
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
            addView(text, LinearLayout.LayoutParams(dp(640), MATCH).apply { marginEnd = dp(Look.EDGE) })
            addView(picture, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    } else {
        column().apply {
            addView(picture, LinearLayout.LayoutParams(MATCH, dp(320)))
            addView(text, fill().apply { topMargin = dp(Look.CARD_GAP) })
        }
    }

/** 남는 세로 공간을 차지해 그 아래 버튼을 화면 바닥에 붙인다. */
internal fun fill() = LinearLayout.LayoutParams(MATCH, 0, 1f)

internal fun matchFrame() = FrameLayout.LayoutParams(MATCH, MATCH)
