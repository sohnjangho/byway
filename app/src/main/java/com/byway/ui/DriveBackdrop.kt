package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.view.View

private const val HALVINGS = 4
private const val DOUBLINGS = 2

/**
 * 주행 화면 글 칸의 배경. 구간 색 위에 코스 사진을 흐리고 옅게 깔아 코스의 분위기를 낸다. 사진이 없는 코스는 구간 색만 쓴다.
 * 지도가 나타나는 자리에서 옅어져 지도와 이어진다. 움직이지 않고 구간이 바뀌면 그 자리에서 색만 바뀐다.
 * [full]이면 지도와 잇지 않고 화면 전체에 깐다. 파티·요약·안내 화면과, 확대 지도가 보이는 주행 화면의 바탕이다(화면 다듬기 설계 4절).
 * [strip]은 사진 띠 때문에 줄인 장면(1/3 홈)의 띠를 쓴다. [band]를 주면 그 띠(화면 dp)에서 옅어진다(둘러보기 글 칸).
 */
@SuppressLint("ViewConstructor")
internal class DriveBackdrop(
    context: Context,
    private val window: WindowClass,
    full: Boolean = false,
    private val strip: Boolean = false,
    private val band: FadeBand? = null,
) : View(context) {
    var full: Boolean = full
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private var tint = context.palette.bg
    private var soft: Bitmap? = null
    private val photo = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        alpha = ((if (context.night) Dimens.DRIVE_PHOTO_NIGHT else Dimens.DRIVE_PHOTO_DAY) * 255).toInt()
    }
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    fun setTint(color: Int) {
        if (color == tint) return
        tint = color
        invalidate()
    }

    /**
     * 작게 줄였다가 늘리면 흐려진다. 글자 뒤에서 사진의 윤곽이 글자와 다투지 않는다.
     * 한 번에 크게 줄이면 화소가 건너뛰어져 모자이크처럼 보이므로 절반씩 줄이고 두 배씩 늘린다.
     */
    fun setPicture(bitmap: Bitmap) {
        var picture = bitmap
        repeat(HALVINGS) { picture = picture.resized(picture.width / 2, picture.height / 2) }
        repeat(DOUBLINGS) { picture = picture.resized(picture.width * 2, picture.height * 2) }
        soft = picture
        invalidate()
    }

    fun clearPicture() {
        soft = null
        invalidate()
    }

    private fun Bitmap.resized(w: Int, h: Int): Bitmap = Bitmap.createScaledBitmap(this, maxOf(1, w), maxOf(1, h), true)

    override fun onDraw(canvas: Canvas) {
        if (full) {
            canvas.drawColor(tint)
            soft?.let { canvas.drawBitmap(it, centerCrop(it, width, height), Rect(0, 0, width, height), photo) }
            return
        }
        val density = resources.displayMetrics.density
        val band = band ?: sceneFadeOnScreen(window, width / density, height / density, strip = strip)
        val from = band.from * density
        val to = band.to * density
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawColor(tint)
        soft?.let {
            val area = if (band.vertical) Rect(0, from.toInt(), width, height) else Rect(0, 0, to.toInt(), height)
            canvas.drawBitmap(it, centerCrop(it, area.width(), area.height()), area, photo)
        }
        mask.shader = if (band.vertical) {
            LinearGradient(0f, from, 0f, to, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        } else {
            // 지도가 짙어지는 띠와 같은 자리에서 옅어진다. 띠가 글 칸 앞 300dp에서 시작하므로 따로 앞당기지 않는다.
            LinearGradient(from, 0f, to, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mask)
        canvas.restoreToCount(layer)
    }
}
