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

/** 글 칸 오른쪽에서 미리 옅어지기 시작하는 길이(dp). 어두운 지도와 만나도 세로 경계가 보이지 않는다. */
private const val SIDE_FADE_LEAD = 260f

/**
 * 주행 화면 글 칸의 배경. 구간 색 위에 코스 사진을 흐리고 옅게 깔아 코스의 분위기를 낸다. 사진이 없는 코스는 구간 색만 쓴다.
 * 지도가 나타나는 자리에서 옅어져 지도와 이어진다. 움직이지 않고 구간이 바뀌면 그 자리에서 색만 바뀐다.
 */
@SuppressLint("ViewConstructor")
internal class DriveBackdrop(context: Context, private val window: WindowClass) : View(context) {
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

    private fun Bitmap.resized(w: Int, h: Int): Bitmap = Bitmap.createScaledBitmap(this, maxOf(1, w), maxOf(1, h), true)

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val band = sceneFadeOnScreen(window, width / density, height / density, strip = window == WindowClass.THIRD)
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
            LinearGradient(from - SIDE_FADE_LEAD * density, 0f, to, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mask)
        canvas.restoreToCount(layer)
    }
}
