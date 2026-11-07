package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** 달리는 코스 없이 주행 중일 때. 글과 조용한 코스 선뿐이고 누를 곳이 없다. */
fun waitingScreen(context: Context): View = with(context) {
    column().apply {
        gravity = Gravity.CENTER
        setBackgroundColor(Look.BACKGROUND)
        val edge = dp(Look.EDGE)
        setPadding(edge, edge, edge, edge)
        addView(WaitingMark(context), LinearLayout.LayoutParams(dp(120), dp(120)))
        addSpaced(label("정차하면 코스를 고를 수 있습니다", Look.DRIVE_SECTION, bold = true).apply { gravity = Gravity.CENTER }, Look.CARD_GAP)
        val note = "주행 중에는 화면을 조작하지 않습니다.\n기어를 P에 두면 코스 목록이 열립니다."
        addSpaced(label(note, Look.SMALL, Look.MUTED).apply { gravity = Gravity.CENTER }, Look.CARD_GAP)
    }
}

private class WaitingMark(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Look.LINE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Look.LINE }
    private val curve = Path().apply {
        moveTo(20f, 96f)
        cubicTo(40f, 70f, 30f, 48f, 56f, 44f)
        cubicTo(82f, 40f, 92f, 30f, 100f, 14f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.scale(width / SIZE, height / SIZE)
        canvas.drawPath(curve, line)
        canvas.drawCircle(56f, 44f, 7f, dot)
        canvas.drawCircle(100f, 14f, 7f, dot)
    }

    private companion object {
        const val SIZE = 120f
    }
}
