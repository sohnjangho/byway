package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.byway.core.course.Course
import com.byway.core.course.CourseScene
import com.byway.core.course.ScenePlace
import com.byway.service.SceneCatalog
import kotlin.math.abs

private const val NIGHT_OPACITY = 0.66f
private const val DAY_OPACITY = 0.86f
private const val PIN_RADIUS = 20f
private const val GLOW_ALPHA = 0.22f
private const val SATELLITE_CREDIT = "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"

/** 코스 둘레 위성 그림과 경로. 밀면 풍경 사진으로 바뀐다. 그림이 없으면 코스 그림으로 대신한다. */
@SuppressLint("ViewConstructor")
internal class SceneView(context: Context, private val window: WindowClass) : FrameLayout(context) {
    private val colors = context.palette
    private val canvasView = SceneCanvas(context, window)
    private val fallback = CoursePathView(context)
    private val photo = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = GONE }
    private val hint = chip("")
    private val photoCredit = chip("")
    private val credit = chip(SATELLITE_CREDIT)
    private val latest = LatestOnly()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * SWIPE_FACTOR
    private var downX = 0f
    private var course: Course? = null
    private var canShowPhoto = false

    init {
        addView(fallback, matchFrame())
        addView(canvasView, matchFrame())
        addView(photo, matchFrame())
        val edge = context.dp(Dimens.SPACE_8)
        addView(hint, LayoutParams(WRAP, context.dp(Dimens.TOUCH_MIN), Gravity.TOP or Gravity.END).apply { topMargin = edge; marginEnd = edge })
        addView(photoCredit, LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = context.dp(Dimens.SPACE_3); marginEnd = edge })
        addView(credit, LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = context.dp(Dimens.SPACE_3); marginEnd = edge })
        hint.setOnClickListener { togglePhoto() }
    }

    private fun chip(text: String): TextView = context.label(text, Dimens.LABEL, colors.onPhoto).apply {
        background = context.rounded(colors.photoScrim, Dimens.RADIUS_SM)
        gravity = Gravity.CENTER
        val inset = context.dp(Dimens.CREDIT_PADDING)
        setPadding(context.dp(Dimens.SPACE_3), inset, context.dp(Dimens.SPACE_3), inset)
    }

    fun show(course: Course, scene: CourseScene?, places: List<ScenePlace>, night: Boolean, sceneryAvailable: Boolean) {
        this.course = course
        canShowPhoto = sceneryAvailable
        showRoute()
        fallback.show(course, emptyList())
        val token = latest.next()
        canvasView.showScene(scene, places, night, SceneCatalog.index(context).sceneSize.x)
        canvasView.visibility = GONE
        fallback.visibility = VISIBLE
        if (scene != null) {
            Covers.loadScene(context, course.id, night) { bitmap ->
                if (latest.isCurrent(token)) {
                    canvasView.setPicture(bitmap)
                    canvasView.visibility = VISIBLE
                    fallback.visibility = GONE
                }
            }
        }
        photo.setImageDrawable(null)
        if (sceneryAvailable) {
            Covers.loadScenery(context, course) { if (latest.isCurrent(token)) photo.setImageBitmap(it) }
            photoCredit.text = course.sceneryCredit
        }
    }

    private fun showRoute() {
        photo.visibility = GONE
        photoCredit.visibility = GONE
        credit.visibility = VISIBLE
        hint.text = "밀어서 풍경 보기"
        hint.visibility = if (canShowPhoto) VISIBLE else GONE
    }

    private fun togglePhoto() {
        if (!canShowPhoto) return
        if (photo.visibility == VISIBLE) {
            showRoute()
        } else {
            photo.visibility = VISIBLE
            photoCredit.visibility = VISIBLE
            credit.visibility = GONE
            hint.text = "경로로 돌아가기"
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) downX = event.x
        return canShowPhoto && event.actionMasked == MotionEvent.ACTION_MOVE && abs(event.x - downX) > slop
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                return canShowPhoto
            }
            MotionEvent.ACTION_UP -> if (abs(event.x - downX) > slop) togglePhoto()
        }
        return canShowPhoto
    }

    private companion object {
        const val SWIPE_FACTOR = 4
    }
}

/** 그림은 그대로 두고 경로는 그 위에 그린다. 지도 라이브러리와 네트워크가 필요 없다. */
private class SceneCanvas(context: Context, private val window: WindowClass) : View(context) {
    private val colors = context.palette
    private var picture: Bitmap? = null
    private var scene: CourseScene? = null
    private var places: List<ScenePlace> = emptyList()
    private var night = true
    private var sceneWidth = 0f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fade = Paint()
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

    fun showScene(scene: CourseScene?, places: List<ScenePlace>, night: Boolean, sceneWidth: Float) {
        this.scene = scene
        this.places = places
        this.night = night
        this.sceneWidth = sceneWidth
        picture = null
        invalidate()
    }

    fun setPicture(bitmap: Bitmap) {
        picture = bitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = picture ?: return
        val density = resources.displayMetrics.density
        val rect = sceneRect(window, width / density, height / density)
        val out = RectF(rect.left * density, rect.top * density, (rect.left + rect.width) * density, (rect.top + rect.height) * density)
        drawPicture(canvas, bitmap, out, density)
        scene?.let { drawRoute(canvas, it, out) }
    }

    private fun drawPicture(canvas: Canvas, bitmap: Bitmap, out: RectF, density: Float) {
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        paint.alpha = ((if (night) NIGHT_OPACITY else DAY_OPACITY) * 255).toInt()
        canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), out, paint)
        val band = sceneFade(window)
        val from = band.from * density
        val to = band.to * density
        val startColor = android.graphics.Color.argb((band.alphaFrom * 255).toInt(), 0, 0, 0)
        fade.shader = if (band.vertical) {
            LinearGradient(0f, out.top + from, 0f, out.top + to, startColor, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(out.left + from, 0f, out.left + to, 0f, startColor, android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
        }
        fade.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fade)
        canvas.restoreToCount(layer)
    }

    private fun drawRoute(canvas: Canvas, scene: CourseScene, out: RectF) {
        if (sceneWidth <= 0f) return
        val scale = out.width() / sceneWidth
        val line = context.dp(Dimens.COURSE_LINE.toInt()).toFloat()
        val paths = scene.lines.map { it.mood to pathOf(it.points.map { p -> Pair(p.x, p.y) }, out, scale) }
        paths.forEach { (mood, path) ->
            stroke.color = colors.moodColor(mood)
            stroke.alpha = (GLOW_ALPHA * 255).toInt()
            stroke.strokeWidth = line * 3
            canvas.drawPath(path, stroke)
        }
        paths.forEach { (_, path) ->
            stroke.color = colors.photoScrim
            stroke.strokeWidth = line + context.dp(6)
            canvas.drawPath(path, stroke)
        }
        paths.forEach { (mood, path) ->
            stroke.color = colors.moodColor(mood)
            stroke.alpha = 255
            stroke.strokeWidth = line
            canvas.drawPath(path, stroke)
        }
        places.forEach { place -> drawPin(canvas, out.left + place.x * scale, out.top + place.y * scale, place.no) }
    }

    private fun pathOf(points: List<Pair<Float, Float>>, out: RectF, scale: Float): Path = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(out.left + x * scale, out.top + y * scale) else lineTo(out.left + x * scale, out.top + y * scale)
        }
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, number: Int) {
        val radius = context.dp(PIN_RADIUS.toInt()).toFloat()
        pinFill.color = colors.photoScrim
        canvas.drawCircle(x, y, radius + context.dp(3), pinFill)
        pinFill.color = colors.onPhoto
        canvas.drawCircle(x, y, radius, pinFill)
        pinText.color = colors.photoScrim
        pinText.textSize = context.dp(Dimens.LABEL.toInt()).toFloat()
        canvas.drawText("$number", x, y + pinText.textSize * 0.36f, pinText)
    }
}
