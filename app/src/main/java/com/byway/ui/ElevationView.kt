package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.FrameLayout
import com.byway.core.course.Course

/** 코스 고도 그래프. 구간마다 그 구간의 색으로 칠한다. */
class ElevationView(context: Context) : View(context) {
    private class Band(val share: Float, val color: Int)

    private var heights: List<Double> = emptyList()
    private var bands: List<Band> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** [sectionMeters]는 구간 거리다. 고도는 코스를 따라 고르게 뽑혀 있어 거리 비율로 구간을 나눈다. */
    fun show(course: Course, sectionMeters: List<Double>) {
        val total = sectionMeters.sum()
        heights = course.elevation
        bands = sectionMeters.zip(course.sections) { meters, section ->
            Band((meters / total).toFloat(), context.palette.moodColor(section.mood))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (heights.size < 2) return
        val low = heights.min()
        val span = (heights.max() - low).coerceAtLeast(1.0)
        val w = width.toFloat()
        val h = height.toFloat()
        fun heightAt(fraction: Float): Float {
            val place = fraction * heights.lastIndex
            val index = place.toInt().coerceAtMost(heights.lastIndex - 1)
            val value = heights[index] + (heights[index + 1] - heights[index]) * (place - index)
            return h - ((value - low) / span).toFloat() * h * TOP_SHARE - h * (1 - TOP_SHARE) / 2
        }
        var start = 0f
        for (band in bands) {
            val end = (start + band.share).coerceAtMost(1f)
            val area = Path().apply {
                moveTo(start * w, h)
                lineTo(start * w, heightAt(start))
                val steps = ((end - start) * heights.lastIndex).toInt().coerceAtLeast(1)
                for (step in 1..steps) {
                    val fraction = start + (end - start) * step / steps
                    lineTo(fraction * w, heightAt(fraction))
                }
                lineTo(end * w, h)
                close()
            }
            paint.color = band.color
            canvas.drawPath(area, paint)
            start = end
        }
    }

    private companion object {
        const val TOP_SHARE = 0.85f
    }
}

/** 고도 자료가 없으면 null 이다. 빈 그래프를 그리지 않는다. */
internal fun Context.elevationChart(course: Course, sectionMeters: List<Double>): View? {
    if (course.elevation.size < 2) return null
    return FrameLayout(this).apply {
        background = rounded(palette.bgDeep, Dimens.RADIUS_SM)
        clipToOutline = true
        addView(ElevationView(context).apply { show(course, sectionMeters) }, matchFrame())
    }
}
