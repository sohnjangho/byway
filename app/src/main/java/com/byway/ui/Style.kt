package com.byway.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 화면들이 함께 쓰는 색과 크기. 차량 화면이라 글자와 터치 영역을 크게 잡는다. */
internal object Look {
    val BACKGROUND = Color.parseColor("#14181D")
    val SURFACE = Color.parseColor("#1E242B")
    val SURFACE_PRESSED = Color.parseColor("#2A323B")
    val INK = Color.parseColor("#F5F1E8")
    val MUTED = Color.parseColor("#9AA3AD")
    val ACCENT = Color.parseColor("#E8A15C")
    val ACCENT_PRESSED = Color.parseColor("#C98744")

    const val TITLE = 56f
    const val HEADING = 40f
    const val BODY = 28f
    const val SMALL = 24f

    const val EDGE = 64
    const val GAP = 24
    const val CORNER = 24
    const val BUTTON_HEIGHT = 104
    const val SMALL_BUTTON_HEIGHT = 80
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

internal fun Context.rounded(color: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(Look.CORNER).toFloat()
}

/** 누름 표시는 페이드나 물결 없이 색만 바꾼다. 주행 화면에 움직임을 만들지 않기 위해서다. */
internal fun Context.pressable(normal: Int, pressed: Int): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed))
    addState(intArrayOf(), rounded(normal))
}

private fun Context.button(title: String, size: Float, height: Int, onClick: () -> Unit) = Button(this).apply {
    text = title
    textSize = size
    isAllCaps = false
    stateListAnimator = null
    minHeight = dp(height)
    setPadding(dp(40), 0, dp(40), 0)
    setOnClickListener { onClick() }
}

internal fun Context.primaryButton(title: String, onClick: () -> Unit): Button =
    button(title, 30f, Look.BUTTON_HEIGHT, onClick).apply {
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Look.BACKGROUND)
        background = pressable(Look.ACCENT, Look.ACCENT_PRESSED)
    }

internal fun Context.quietButton(title: String, onClick: () -> Unit): Button =
    button(title, Look.SMALL, Look.SMALL_BUTTON_HEIGHT, onClick).apply {
        setTextColor(Look.INK)
        background = pressable(Look.SURFACE, Look.SURFACE_PRESSED)
    }

internal fun Context.chip(title: String) = label(title, Look.SMALL, Look.ACCENT).apply {
    background = rounded(Look.BACKGROUND)
    setPadding(dp(20), dp(6), dp(20), dp(6))
}

internal fun Context.courseMap(): CoursePathView = CoursePathView(this).apply { background = rounded(Look.SURFACE) }

internal fun Context.column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

internal fun LinearLayout.addSpaced(view: View, top: Int) {
    addView(
        view,
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(top)
        },
    )
}

/** 왼쪽에 글과 버튼, 오른쪽에 코스 그림을 두는 공통 틀. */
internal fun Context.twoColumns(left: View, right: View, leftWeight: Float = 1f): LinearLayout =
    LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Look.BACKGROUND)
        val edge = dp(Look.EDGE)
        setPadding(edge, edge, edge, edge)
        addView(
            left,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, leftWeight).apply { marginEnd = edge },
        )
        addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
    }

/** 남는 세로 공간을 차지해 그 아래 버튼을 화면 바닥에 붙인다. */
internal fun fill() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
