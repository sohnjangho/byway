package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.byway.core.course.Course
import com.byway.core.course.Stop
import com.byway.core.geo.GeoPoint
import kotlin.math.cos

/** 코스 모양과 내비에 올라가는 지점, 현재 위치를 그린다. 지도가 아니라 코스 그림이다. */
class CoursePathView(context: Context) : View(context) {
    private val colors = context.palette
    private var course: Course? = null
    private var stops: List<Stop> = emptyList()
    private var position: GeoPoint? = null
    private var thin = false

    /** 코스 선은 크기가 바뀔 때만 다시 만든다. 주행 중 점이 움직일 때마다 1,000개 넘는 좌표를 다시 돌지 않는다. */
    private var frame: Frame? = null
    private var lines: List<Pair<Path, Int>> = emptyList()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val stop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.ink }
    private val car = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent }

    /** [thin]은 카드 썸네일용 가는 선이다. */
    fun show(course: Course, stops: List<Stop>, thin: Boolean = false) {
        this.course = course
        this.stops = stops
        this.thin = thin
        prepare()
        invalidate()
    }

    fun moveTo(point: GeoPoint?) {
        position = point
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = prepare()

    private fun prepare() {
        val course = course ?: return
        if (width == 0 || height == 0 || course.path.size < 2) return
        val frame = Frame(course.path, width.toFloat(), height.toFloat()).also { frame = it }
        lines = course.sections.map { section ->
            val end = minOf(section.endIndex + 1, course.path.lastIndex)
            val points = course.path.subList(section.startIndex, end + 1)
            val drawn = Path().apply {
                moveTo(frame.x(points.first()), frame.y(points.first()))
                points.drop(1).forEach { lineTo(frame.x(it), frame.y(it)) }
            }
            drawn to colors.moodColor(section.mood)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val frame = frame ?: return
        val density = resources.displayMetrics.density
        val width = density * if (thin) Dimens.COURSE_LINE_THUMB else Dimens.COURSE_LINE
        // 코스 선은 어두운 외곽선 위에 구간 색을 그린다. 주간의 흰 면에서도 선이 묻히지 않는다.
        line.strokeWidth = width + density * Dimens.COURSE_OUTLINE
        line.color = colors.photoScrim
        lines.forEach { (drawn, _) -> canvas.drawPath(drawn, line) }
        line.strokeWidth = width
        lines.forEach { (drawn, color) ->
            line.color = color
            canvas.drawPath(drawn, line)
        }
        stops.forEach { canvas.drawCircle(frame.x(it.point), frame.y(it.point), density * Dimens.STOP_RADIUS, stop) }
        position?.let { canvas.drawCircle(frame.x(it), frame.y(it), density * Dimens.CAR_RADIUS, car) }
    }

    /** 코스를 칸 안에 맞추는 좌표 변환. */
    private class Frame(path: List<GeoPoint>, width: Float, height: Float) {
        private val minLon = path.minOf { it.lon }
        private val minLat = path.minOf { it.lat }

        // 경도 1도는 위도 1도보다 짧다. 그대로 그리면 코스가 옆으로 늘어난다.
        private val lonScale = cos(Math.toRadians((minLat + path.maxOf { it.lat }) / 2))
        private val spanX = (path.maxOf { it.lon } - minLon) * lonScale
        private val spanY = path.maxOf { it.lat } - minLat
        private val pad = minOf(width, height) * 0.1f
        private val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
        private val left = (width - spanX * scale) / 2
        private val bottom = height - (height - spanY * scale) / 2

        fun x(p: GeoPoint) = (left + (p.lon - minLon) * lonScale * scale).toFloat()
        fun y(p: GeoPoint) = (bottom - (p.lat - minLat) * scale).toFloat()
    }
}
