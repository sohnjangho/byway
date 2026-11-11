package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/**
 * 홈 지도 장면과 파티 확대 지도가 함께 쓰는 그림(주행 화면 홈 맞춤 설계 2절).
 * 지도 불투명도, 글 칸 쪽 띠, 세 겹 코스 선, 번호 원, 주황 위치 점이 두 지도에서 같다.
 */
internal object MapMarks {
    private const val NIGHT_OPACITY = 0.66f
    private const val DAY_OPACITY = 0.86f
    private const val PIN_RADIUS = 20
    private const val GLOW_ALPHA = 0.22f
    private const val MARKER_HALO = 30
    private const val MARKER_HALO_ALPHA = 80
    private const val MARKER_DOT = 12
    private const val MARKER_RING = 4
    private const val CASING = 6
    private const val NUMBER_BASELINE = 0.36f

    /** 지도 그림을 까는 층의 불투명도(0~255). */
    fun mapAlpha(night: Boolean): Int = ((if (night) NIGHT_OPACITY else DAY_OPACITY) * 255).toInt()

    /** 띠로 지도를 가린다. 가로 띠는 글 칸 쪽이 [FadeBand.alphaFrom]만 남고, 세로 띠는 아래로 사라진다. [offset]은 띠 좌표의 원점(px)이다. */
    fun fade(canvas: Canvas, paint: Paint, band: FadeBand, offset: Float, density: Float, width: Float, height: Float) {
        val from = offset + band.from * density
        val to = offset + band.to * density
        val start = Color.argb((band.alphaFrom * 255).toInt(), 0, 0, 0)
        paint.shader = if (band.vertical) {
            LinearGradient(0f, from, 0f, to, start, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(from, 0f, to, 0f, start, Color.BLACK, Shader.TileMode.CLAMP)
        }
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    /** 구간 색 빛 번짐, 어두운 테두리, 구간 색 선의 세 겹. 지도 위에서는 밤 팔레트의 밝은 구간 색을 쓴다. */
    fun route(canvas: Canvas, context: Context, paths: List<Pair<String?, Path>>, stroke: Paint, colors: Palette) {
        val line = context.dp(Dimens.COURSE_LINE.toInt()).toFloat()
        paths.forEach { (mood, path) ->
            stroke.color = NightPalette.moodColor(mood)
            stroke.alpha = (GLOW_ALPHA * 255).toInt()
            stroke.strokeWidth = line * 3
            canvas.drawPath(path, stroke)
        }
        paths.forEach { (_, path) ->
            stroke.color = colors.photoScrim
            stroke.strokeWidth = line + context.dp(CASING)
            canvas.drawPath(path, stroke)
        }
        paths.forEach { (mood, path) ->
            stroke.color = NightPalette.moodColor(mood)
            stroke.alpha = 255
            stroke.strokeWidth = line
            canvas.drawPath(path, stroke)
        }
    }

    /** 코스 주변 장소 번호. 밝은 일반 지도에서는 어두운 원으로 그린다. */
    fun place(canvas: Canvas, context: Context, x: Float, y: Float, number: Int, opacity: Float, dark: Boolean, fill: Paint, text: Paint, colors: Palette) {
        if (opacity <= 0f) return
        val radius = context.dp(PIN_RADIUS).toFloat()
        val face = if (dark) colors.ink else colors.onPhoto
        val edge = if (dark) colors.bg else colors.photoScrim
        fill.color = edge
        fill.alpha = (fill.alpha * opacity).toInt()
        canvas.drawCircle(x, y, radius + context.dp(3), fill)
        fill.color = face
        fill.alpha = (fill.alpha * opacity).toInt()
        canvas.drawCircle(x, y, radius, fill)
        text.color = edge
        text.alpha = (text.alpha * opacity).toInt()
        text.textSize = context.dp(Dimens.LABEL.toInt()).toFloat()
        canvas.drawText("$number", x, y + text.textSize * NUMBER_BASELINE, text)
    }

    /** 지금 자리. 번호와 헷갈리지 않게 흰 테두리와 옅은 후광이 있는 주황 점이다. */
    fun marker(canvas: Canvas, context: Context, x: Float, y: Float, fill: Paint, colors: Palette) {
        fill.color = NightPalette.accent
        fill.alpha = MARKER_HALO_ALPHA
        canvas.drawCircle(x, y, context.dp(MARKER_HALO).toFloat(), fill)
        fill.color = colors.onPhoto
        canvas.drawCircle(x, y, context.dp(MARKER_DOT + MARKER_RING).toFloat(), fill)
        fill.color = NightPalette.accent
        canvas.drawCircle(x, y, context.dp(MARKER_DOT).toFloat(), fill)
    }
}
