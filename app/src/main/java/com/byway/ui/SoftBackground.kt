package com.byway.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import com.byway.core.course.Course
import kotlin.math.max

/** 코스 풍경을 흐리게 깐 바탕. 뷰 크기를 따르고 이미지가 뷰를 밀어내지 않는다. 이 위 글자는 ink 만 쓴다. */
internal fun View.softBackground(course: Course, radius: Int = Dimens.RADIUS_MD) {
    val face = SoftDrawable(context.palette.bgRaised, context.palette.softWash, context.dp(radius).toFloat())
    background = face
    clipToOutline = true
    Covers.loadSoft(context, course.id) { face.image = it }
}

private class SoftDrawable(private val base: Int, private val wash: Int, private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    var image: Bitmap? = null
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(base)
        image?.let { canvas.drawBitmap(it, centerCrop(it), bounds, paint) }
        canvas.drawColor(wash)
    }

    private fun centerCrop(picture: Bitmap): Rect {
        val scale = max(bounds.width().toFloat() / picture.width, bounds.height().toFloat() / picture.height)
        val width = (bounds.width() / scale).toInt()
        val height = (bounds.height() / scale).toInt()
        val left = (picture.width - width) / 2
        val top = (picture.height - height) / 2
        return Rect(left, top, left + width, top + height)
    }

    override fun getOutline(outline: Outline) = outline.setRoundRect(bounds, radius)

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Drawable 규약", ReplaceWith("PixelFormat.OPAQUE"))
    override fun getOpacity() = PixelFormat.OPAQUE
}
