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
import kotlin.math.max

private const val OPACITY = 0.4f
private const val SOLID_PART = 0.45f
private const val TOP_FADE = 0.3f

/** 코스 설명 뒤의 흐린 풍경. 가장자리를 지워 위성 그림과 이어지게 한다. */
@SuppressLint("ViewConstructor")
internal class SoftPatch(context: Context, private val below: Boolean) : View(context) {
    private var picture: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (OPACITY * 255).toInt() }
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    fun setPicture(bitmap: Bitmap) {
        picture = bitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = picture ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawBitmap(bitmap, crop(bitmap), Rect(0, 0, width, height), paint)
        if (below) {
            maskWith(canvas, LinearGradient(0f, 0f, 0f, h * TOP_FADE, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP))
        } else {
            maskWith(canvas, LinearGradient(w * SOLID_PART, 0f, w, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP))
            maskWith(canvas, LinearGradient(0f, h * (1 - SOLID_PART), 0f, h, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP))
        }
        canvas.restoreToCount(layer)
    }

    private fun maskWith(canvas: Canvas, shader: Shader) {
        mask.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mask)
    }

    private fun crop(picture: Bitmap): Rect {
        val scale = max(width.toFloat() / picture.width, height.toFloat() / picture.height)
        val w = (width / scale).toInt()
        val h = (height / scale).toInt()
        val left = (picture.width - w) / 2
        val top = (picture.height - h) / 2
        return Rect(left, top, left + w, top + h)
    }
}
