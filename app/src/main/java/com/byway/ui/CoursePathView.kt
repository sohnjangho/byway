package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import com.byway.core.course.Course
import com.byway.core.course.Stop
import com.byway.core.geo.GeoFrame
import com.byway.core.geo.GeoPoint
import kotlin.math.cos

/**
 * 코스 모양과 내비에 올라가는 지점, 현재 위치를 그린다. 지도가 아니라 코스 그림이다.
 * [zoom]으로 범위를 주면 그 범위를 칸에 꽉 채워 그리고(파티 확대 지도), 닉네임·축척·출처·먼 멤버 수를 함께 적는다.
 */
/** 파티 핀 아바타 반지름(dp). */
internal const val PIN_RADIUS_DP = 22f

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
    private var pins: List<com.byway.ui.party.MapPin> = emptyList()

    /** 파티 확대 지도의 범위와 범위 밖 먼 멤버 수. 범위가 없으면 코스 전체를 맞춘다. */
    private var zoomed: GeoFrame? = null
    private var far = 0
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val scale = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    /** [thin]은 카드 썸네일용 가는 선이다. */
    fun show(course: Course, stops: List<Stop>, thin: Boolean = false) {
        this.course = course
        this.stops = stops
        this.thin = thin
        prepare()
        invalidate()
    }

    /** 파티 멤버 핀. 위치가 바뀌면 그 자리에 다시 그린다(움직임 효과 없음). */
    fun setPins(next: List<com.byway.ui.party.MapPin>) {
        if (pins == next) return
        pins = next
        invalidate()
    }

    /** 범위는 바뀔 때만 코스 선을 다시 만든다. 범위가 바뀌는 순간 애니메이션 없이 한 번에 바뀐다. */
    fun zoom(frame: GeoFrame?, far: Int) {
        if (frame == zoomed && far == this.far) return
        zoomed = frame
        this.far = far
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
        val frame = (zoomed?.let { Frame.of(it, width.toFloat(), height.toFloat()) } ?: Frame.of(course.path, width.toFloat(), height.toFloat()))
            .also { frame = it }
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
        if (zoomed != null) canvas.drawColor(colors.bg)
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
        pins.forEach { com.byway.ui.party.PartyPins.draw(canvas, context, frame.x(it.point), frame.y(it.point), it.avatar, density * PIN_RADIUS_DP, colors.onPhoto, colors.photoScrim) }
        zoomed?.let { drawZoomNotes(canvas, frame, it, density) }
    }

    /** 확대 지도의 글. 핀 아래 닉네임, 왼쪽 아래 축척, 오른쪽 아래 출처, 왼쪽 위 먼 멤버 수다. 글자는 바탕색 테두리로 선 위에서도 읽힌다. */
    private fun drawZoomNotes(canvas: Canvas, frame: Frame, zoom: GeoFrame, density: Float) {
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Dimens.LABEL, resources.displayMetrics)
        val edge = density * Dimens.SPACE_4
        text.textSize = size
        halo.textSize = size
        halo.strokeWidth = size * 0.3f
        halo.color = colors.bg
        fun write(value: String, x: Float, y: Float, color: Int, align: Paint.Align) {
            text.textAlign = align
            halo.textAlign = align
            text.color = color
            canvas.drawText(value, x, y, halo)
            canvas.drawText(value, x, y, text)
        }
        pins.filter { it.name.isNotEmpty() }.forEach {
            write(it.name, frame.x(it.point), frame.y(it.point) + size * 1.3f, colors.ink, Paint.Align.CENTER)
        }
        if (far > 0) write("멀리 있는 멤버 ${far}명", edge, edge + size, colors.ink, Paint.Align.LEFT)
        val meters = scaleBarMeters(zoom.widthMeters)
        val length = (meters / zoom.widthMeters * width).toFloat()
        val y = height - edge
        scale.color = colors.ink
        scale.strokeWidth = density * 3
        canvas.drawLine(edge, y, edge + length, y, scale)
        write(scaleLabel(meters), edge, y - size * 0.6f, colors.ink, Paint.Align.LEFT)
        course?.attribution?.takeIf { it.isNotBlank() }?.let { write(it, width - edge, y, colors.inkSecondary, Paint.Align.RIGHT) }
    }

    /** 위경도 범위를 칸 안에 맞추는 좌표 변환. 코스 전체는 여백 10%로, 확대 범위는 여백 없이 맞춘다. */
    private class Frame(
        private val minLon: Double,
        private val minLat: Double,
        maxLon: Double,
        maxLat: Double,
        width: Float,
        height: Float,
        padRatio: Float,
    ) {
        // 경도 1도는 위도 1도보다 짧다. 그대로 그리면 코스가 옆으로 늘어난다.
        private val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
        private val spanX = (maxLon - minLon) * lonScale
        private val spanY = maxLat - minLat
        private val pad = minOf(width, height) * padRatio
        private val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
        private val left = (width - spanX * scale) / 2
        private val bottom = height - (height - spanY * scale) / 2

        fun x(p: GeoPoint) = (left + (p.lon - minLon) * lonScale * scale).toFloat()
        fun y(p: GeoPoint) = (bottom - (p.lat - minLat) * scale).toFloat()

        companion object {
            fun of(path: List<GeoPoint>, width: Float, height: Float) =
                Frame(path.minOf { it.lon }, path.minOf { it.lat }, path.maxOf { it.lon }, path.maxOf { it.lat }, width, height, 0.1f)

            fun of(zoom: GeoFrame, width: Float, height: Float) =
                Frame(zoom.west, zoom.south, zoom.east, zoom.north, width, height, 0f)
        }
    }
}
