package com.byway.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.byway.core.course.Course
import com.byway.core.course.ScenePlace
import com.byway.core.course.Stop
import com.byway.core.geo.GeoFrame
import com.byway.core.geo.GeoPoint
import com.byway.core.geo.MapProjection
import com.byway.core.geo.Viewport
import com.byway.core.geo.pointAt
import com.byway.core.geo.tileZoom
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/**
 * 코스 모양과 내비에 올라가는 지점, 현재 위치를 그린다. 지도가 아니라 코스 그림이다.
 * [zoom]으로 범위를 주면 파티 확대 지도가 된다. 홈 지도 장면과 같은 지도 조각, 세 겹 선, 번호, 위치 점으로 그리고
 * 닉네임·축척·먼 멤버 수를 함께 적는다(주행 화면 홈 맞춤 설계 2절). 출처는 화면의 지도 칩이 적는다.
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

    /** 확대 범위를 맞출 칸(px). 없으면 뷰 전체다. 2/3 칸은 글 칸 오른쪽의 지도 자리이고, 지도는 글 칸 아래까지 이어진다. */
    var zoomFit: Viewport? = null
        set(value) {
            if (field == value) return
            field = value
            prepare()
            invalidate()
        }

    /** 지도를 까는 창. 주면 확대 중에 홈 장면과 같은 지도 조각과 글 칸 쪽 띠를 깐다. */
    var mapWindow: WindowClass? = null

    /** 확대 지도에 번호로 찍는 코스 주변 장소. 자리는 코스 시작에서 잰 거리로 찾는다. */
    private var places: List<ScenePlace> = emptyList()
    private var projection: MapProjection? = null
    private var zoomLines: List<Pair<String?, Path>> = emptyList()
    private var placeSpots: List<Triple<Float, Float, Int>> = emptyList()
    private val tileRect = RectF()
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val layerPaint = Paint()
    private val fadePaint = Paint()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pinFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

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

    fun showPlaces(next: List<ScenePlace>) {
        places = next
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
        val zoom = zoomed
        if (zoom != null) {
            prepareZoom(course, zoom)
            return
        }
        projection = null
        val frame = Frame.of(course.path, width.toFloat(), height.toFloat()).also { frame = it }
        lines = course.sections.map { section ->
            val drawn = Path().apply {
                val points = sectionPoints(course, section)
                moveTo(frame.x(points.first()), frame.y(points.first()))
                points.drop(1).forEach { lineTo(frame.x(it), frame.y(it)) }
            }
            drawn to colors.moodColor(section.mood)
        }
    }

    private fun sectionPoints(course: Course, section: com.byway.core.course.Section): List<GeoPoint> {
        val end = minOf(section.endIndex + 1, course.path.lastIndex)
        return course.path.subList(section.startIndex, end + 1)
    }

    private fun prepareZoom(course: Course, zoom: GeoFrame) {
        val map = MapProjection(zoom, zoomFit ?: Viewport(0f, 0f, width.toFloat(), height.toFloat())).also { projection = it }
        zoomLines = course.sections.map { section ->
            val points = sectionPoints(course, section)
            section.mood to Path().apply {
                moveTo(map.x(points.first()), map.y(points.first()))
                points.drop(1).forEach { lineTo(map.x(it), map.y(it)) }
            }
        }
        placeSpots = places.mapNotNull { place -> pointAt(course.path, place.km)?.let { Triple(map.x(it), map.y(it), place.no) } }
    }

    override fun onDraw(canvas: Canvas) {
        val map = projection
        if (zoomed != null) {
            if (map != null) drawZoom(canvas, map)
            return
        }
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
        pins.forEach { com.byway.ui.party.PartyPins.draw(canvas, context, frame.x(it.point), frame.y(it.point), it.avatar, density * PIN_RADIUS_DP, colors.onPhoto, colors.photoScrim) }
    }

    /** 홈 지도 장면과 같은 순서로 그린다: 지도, 코스 선, 번호, 위치 점, 멤버 핀, 글. */
    private fun drawZoom(canvas: Canvas, map: MapProjection) {
        val density = resources.displayMetrics.density
        mapWindow?.let { drawTiles(canvas, map, it, density) }
        MapMarks.route(canvas, context, zoomLines, stroke, colors)
        val dark = darkPinsOn(currentMapStyle(), context.night)
        placeSpots.forEach { (x, y, no) -> MapMarks.place(canvas, context, x, y, no, 1f, dark, pinFill, pinText, colors) }
        position?.let { MapMarks.marker(canvas, context, map.x(it), map.y(it), pinFill, colors) }
        pins.forEach { com.byway.ui.party.PartyPins.draw(canvas, context, map.x(it.point), map.y(it.point), it.avatar, density * PIN_RADIUS_DP, colors.onPhoto, colors.photoScrim) }
        drawZoomNotes(canvas, map, density)
    }

    /** 받은 조각만 그린다. 아직 없거나 받지 못한 자리는 뒤의 분위기 색이 보인다. 조각이 오면 다시 그린다. */
    private fun drawTiles(canvas: Canvas, map: MapProjection, window: WindowClass, density: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val night = context.night
        val style = currentMapStyle()
        layerPaint.alpha = MapMarks.mapAlpha(night)
        val layer = canvas.saveLayer(0f, 0f, w, h, layerPaint)
        map.tilesFor(w, h, tileZoom(map.pxPerWorld)).forEach { spot ->
            val tile = MapTileCache.get(context, style, night, spot.z, spot.x, spot.y) { invalidate() } ?: return@forEach
            // 조각 사이에 실선이 비치지 않게 바깥쪽 화소로 맞춘다.
            tileRect.set(floor(spot.left), floor(spot.top), ceil(spot.left + spot.size), ceil(spot.top + spot.size))
            canvas.drawBitmap(tile, null, tileRect, tilePaint)
        }
        MapMarks.fade(canvas, fadePaint, sceneFadeOnScreen(window, w / density, h / density), 0f, density, w, h)
        canvas.restoreToCount(layer)
    }

    /** 확대 지도의 글. 핀 아래 닉네임, 지도 자리 왼쪽 아래 축척, 왼쪽 위 먼 멤버 수다. 글자는 바탕색 테두리로 선 위에서도 읽힌다. */
    private fun drawZoomNotes(canvas: Canvas, map: MapProjection, density: Float) {
        val fit = zoomFit ?: Viewport(0f, 0f, width.toFloat(), height.toFloat())
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Dimens.LABEL, resources.displayMetrics)
        val edge = density * Dimens.SPACE_4
        val left = fit.left + edge
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
            write(it.name, map.x(it.point), map.y(it.point) + size * 1.3f, colors.ink, Paint.Align.CENTER)
        }
        if (far > 0) write("멀리 있는 멤버 ${far}명", left, fit.top + edge + size, colors.ink, Paint.Align.LEFT)
        val meters = scaleBarMeters(map.metersPerPx * fit.width)
        val length = (meters / map.metersPerPx).toFloat()
        val y = fit.top + fit.height - edge
        scale.color = colors.ink
        scale.strokeWidth = density * 3
        canvas.drawLine(left, y, left + length, y, scale)
        write(scaleLabel(meters), left, y - size * 0.6f, colors.ink, Paint.Align.LEFT)
    }

    /** 위경도 범위를 칸 안에 맞추는 좌표 변환. 코스 전체를 여백 10%로 맞춘다. */
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
        }
    }
}
