package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.byway.core.course.Course

/** 구간 막대. 칸 길이는 구간 거리에 비례하고 지나간 만큼 구간 색으로 채운다. 값은 건너뛰며 바뀌고 움직임은 없다. */
class SectionBar(context: Context, private val shares: List<Float>, private val colors: List<Int>) : View(context) {
    private var filled: List<Float> = shares.map { 0f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slot = RectF()

    fun show(filled: List<Float>) {
        if (filled == this.filled) return
        this.filled = filled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val gap = Dimens.BAR_GAP * density
        val radius = height / 2f
        val usable = width - gap * (shares.size - 1)
        var left = 0f
        shares.forEachIndexed { i, share ->
            val length = usable * share
            slot.set(left, 0f, left + length, height.toFloat())
            paint.color = colors[i]
            paint.alpha = EMPTY_ALPHA
            canvas.drawRoundRect(slot, radius, radius, paint)
            if (filled[i] > 0f) {
                slot.right = left + length * filled[i]
                paint.alpha = 255
                canvas.drawRoundRect(slot, radius, radius, paint)
            }
            left += length + gap
        }
    }

    private companion object {
        const val EMPTY_ALPHA = 64
    }
}

internal fun Context.sectionBar(book: CourseBook, course: Course): SectionBar {
    val meters = book.sectionMeters(course)
    val total = meters.sum()
    return SectionBar(this, meters.map { (it / total).toFloat() }, course.sections.map { palette.moodColor(it.mood) })
}
