package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.byway.core.course.CourseScene
import com.byway.core.course.Px

/**
 * 주행 화면의 지도. 홈과 같은 지도 그림에서 경로 둘레만 보여 주고 그 위에 코스 선과 위치 점을 그린다.
 * 그림과 선은 움직이지 않고, 달리는 동안 바뀌는 것은 위치 점뿐이다.
 */
@SuppressLint("ViewConstructor")
internal class DriveMapView(context: Context, courseId: String, private val scene: CourseScene) : View(context) {
    private val night = context.night
    private var picture: Bitmap? = null
    private var marker: Px? = null
    private val points = scene.lines.flatMap { it.points }
    private val pictureArea = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = ((if (night) NIGHT_OPACITY else DAY_OPACITY) * 255).toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        background = context.rounded(context.palette.bgRaised)
        clipToOutline = true
        Covers.loadScene(context, courseId, night) {
            picture = it
            invalidate()
        }
    }

    fun moveTo(at: Px?) {
        if (at == marker) return
        marker = at
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0 || points.isEmpty()) return
        val crop = driveCrop(
            width.toFloat(), height.toFloat(),
            points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
        )
        val scale = width / crop.width
        picture?.let {
            val toBitmap = it.width / SCENE_WIDTH
            pictureArea.set(
                (crop.left * toBitmap).toInt(), (crop.top * toBitmap).toInt(),
                ((crop.left + crop.width) * toBitmap).toInt(), ((crop.top + crop.height) * toBitmap).toInt(),
            )
            canvas.drawBitmap(it, pictureArea, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        }
        val paths = scene.lines.map { line ->
            line.mood to Path().apply {
                line.points.forEachIndexed { i, p ->
                    val x = (p.x - crop.left) * scale
                    val y = (p.y - crop.top) * scale
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        }
        context.drawRouteLayers(canvas, paths, stroke, context.dp(Dimens.COURSE_LINE.toInt()).toFloat())
        marker?.let { context.drawRouteMarker(canvas, (it.x - crop.left) * scale, (it.y - crop.top) * scale, fill) }
    }
}
