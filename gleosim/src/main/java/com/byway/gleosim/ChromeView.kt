package com.byway.gleosim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/**
 * Gleo 크롬(디자인 가이드 Status): 화면 위 가운데에서 아래로 번지는 빛. 색으로 대화 상태를 나눈다.
 * 듣는 중은 파랑, 생각 중은 청록, 말하는 중·되묻는 중은 보라다.
 */
class ChromeView(context: Context) : View(context) {
    enum class Status(val core: Int, val edge: Int) {
        LISTENING(Color.parseColor("#3A35F2"), Color.parseColor("#8E9BFF")),
        THINKING(Color.parseColor("#3A86A8"), Color.parseColor("#9CCFE0")),
        SPEAKING(Color.parseColor("#B57BF0"), Color.parseColor("#C9DBFF")),
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var status: Status = Status.LISTENING
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // 원형 그라데이션을 세로로 눌러 위 가장자리에 붙은 넓은 타원 빛을 만든다.
        val squash = h / (w / 2f)
        canvas.save()
        canvas.scale(1f, squash, w / 2f, 0f)
        paint.shader = RadialGradient(
            w / 2f, 0f, w / 2f,
            intArrayOf(status.core, status.core, withAlpha(status.edge, 0xB0), Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f, 0.65f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(w / 2f, 0f, w / 2f, paint)
        canvas.restore()
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
