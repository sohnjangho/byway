package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** 코스 고도 그래프. 구간을 주면 구간 색으로, 주지 않으면 한 색 실루엣으로 그린다. */
class ElevationView(context: Context) : View(context) {
    private class Band(val share: Float, val color: Int)

    private var heights: List<Double> = emptyList()
    private var bands: List<Band> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun silhouette(elevation: List<Double>) {
        show(elevation, listOf(Band(1f, Look.LINE)))
    }

    /** [sectionMeters]는 구간 거리, [moods]는 구간 분위기다. 고도는 코스를 따라 고르게 뽑혀 있다. */
    fun sections(elevation: List<Double>, sectionMeters: List<Double>, moods: List<String?>) {
        val total = sectionMeters.sum()
        show(elevation, sectionMeters.zip(moods) { meters, mood -> Band((meters / total).toFloat(), moodColor(mood)) })
    }

    private fun show(elevation: List<Double>, bands: List<Band>) {
        heights = elevation
        this.bands = bands
        visibility = if (elevation.size < 2) GONE else VISIBLE
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
