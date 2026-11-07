package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.byway.core.course.Stop
import com.byway.core.geo.GeoPoint
import kotlin.math.cos

/** 코스 모양과 내비에 올라가는 지점, 현재 위치를 그린다. 지도가 아니라 코스 그림이다. */
class CoursePathView(context: Context) : View(context) {
    private var path: List<GeoPoint> = emptyList()
    private var stops: List<Stop> = emptyList()
    private var position: GeoPoint? = null

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E8A15C")
    }
    private val stop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F1E8") }
    private val car = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FC3A1") }

    fun show(path: List<GeoPoint>, stops: List<Stop>) {
        this.path = path
        this.stops = stops
        invalidate()
    }

    fun moveTo(point: GeoPoint?) {
        position = point
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (path.size < 2) return
        val minLat = path.minOf { it.lat }
        val maxLat = path.maxOf { it.lat }
        val minLon = path.minOf { it.lon }
        val maxLon = path.maxOf { it.lon }
        // 경도 1도는 위도 1도보다 짧다. 그대로 그리면 코스가 옆으로 늘어난다.
        val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = (maxLon - minLon) * lonScale
        val spanY = maxLat - minLat
        val shortSide = minOf(width, height).toFloat()
        val pad = shortSide * 0.1f
        val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
        val left = (width - spanX * scale) / 2
        val bottom = height - (height - spanY * scale) / 2
        fun x(p: GeoPoint) = (left + (p.lon - minLon) * lonScale * scale).toFloat()
        fun y(p: GeoPoint) = (bottom - (p.lat - minLat) * scale).toFloat()

        val unit = (shortSide / 100).coerceIn(3f, 8f)
        line.strokeWidth = unit
        val drawn = Path().apply {
            moveTo(x(path.first()), y(path.first()))
            path.drop(1).forEach { lineTo(x(it), y(it)) }
        }
        canvas.drawPath(drawn, line)
        stops.forEach { canvas.drawCircle(x(it.point), y(it.point), unit * 1.2f, stop) }
        position?.let { canvas.drawCircle(x(it), y(it), unit * 2.4f, car) }
    }
}
