package com.byway.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.byway.core.course.AtlasSpot
import com.byway.core.course.Box
import com.byway.core.course.Course
import com.byway.service.SceneCatalog

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 24f
private const val ZOOM_STEP = 1.6f
private const val MOVE_MS = 900L
private const val REGION_LABEL_MAX = 1.6f
private const val CITY_LABEL_MAX = 14f
private const val NAMED_MIN = 2.4f
private const val CLOSE_RATIO = 0.6f
private const val SATELLITE_CREDIT = "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"

/** 지도에서 어느 쪽을 어둡게 눌러 글 칸과 잇는지. */
internal class AtlasShade(val fromRight: Boolean, val solid: Float, val clear: Float)

/** 한국 위성 지도. 코스를 고르면 그 자리로 확대되고 코스 장면이 제자리에 겹친다. */
@SuppressLint("ViewConstructor")
internal class AtlasView(context: Context) : FrameLayout(context) {
    private val canvasView = AtlasCanvas(context)
    private val colors = context.palette

    var onCamera: (AtlasCamera) -> Unit
        get() = canvasView.onCamera
        set(value) { canvasView.onCamera = value }
    var onPick: (String) -> Unit
        get() = canvasView.onPick
        set(value) { canvasView.onPick = value }

    init {
        addView(canvasView, matchFrame())
    }

    fun show(tab: Tab, window: WindowClass, courses: List<Course>, chosen: String?, camera: AtlasCamera?, night: Boolean, shade: AtlasShade?) {
        canvasView.configure(atlasFocus(tab, window), courses, tab == Tab.MINE, night, shade)
        canvasView.setChosen(chosen)
        canvasView.setCamera(camera ?: canvasView.overview())
    }

    fun overview(): AtlasCamera = canvasView.overview()

    fun cameraFor(courseId: String): AtlasCamera? = canvasView.cameraFor(courseId)

    fun focusOn(camera: AtlasCamera, chosen: String?) {
        canvasView.setChosen(chosen)
        canvasView.moveTo(camera)
    }

    /** 확대 단추, 안내, 위성 출처. [rightDp]는 오른쪽 글 칸이 차지한 폭이다. */
    fun addChrome(rightDp: Int, resetLabel: String, onReset: () -> Unit) {
        val edge = context.dp(Dimens.SPACE_6) + context.dp(rightDp)
        val tools = context.column().apply {
            addView(tool("+") { canvasView.zoom(ZOOM_STEP) }, toolSize())
            addSpaced(tool("−") { canvasView.zoom(1 / ZOOM_STEP) }, Dimens.SPACE_2)
            addSpaced(tool(resetLabel, onReset), Dimens.SPACE_2)
        }
        addView(tools, LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = edge
            bottomMargin = context.dp(Dimens.ATLAS_TOOLS_BOTTOM)
        })
        val credit = context.label(SATELLITE_CREDIT, Dimens.LABEL, colors.onPhoto).apply {
            background = context.rounded(colors.photoScrim, Dimens.RADIUS_SM)
            val inset = context.dp(Dimens.CREDIT_PADDING)
            setPadding(context.dp(Dimens.SPACE_3), inset, context.dp(Dimens.SPACE_3), inset)
            maxWidth = context.dp(Dimens.ATLAS_CREDIT_WIDTH)
        }
        addView(credit, LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = edge + context.dp(Dimens.TOUCH_MIN + Dimens.SPACE_3)
            bottomMargin = context.dp(Dimens.SPACE_2)
        })
    }

    private fun toolSize() = LinearLayout.LayoutParams(context.dp(Dimens.TOUCH_MIN), context.dp(Dimens.TOUCH_MIN))

    private fun tool(text: String, onClick: () -> Unit): View = context.label(text, if (text.length == 1) Dimens.HEADING else Dimens.SMALL, colors.onPhoto, bold = true).apply {
        gravity = Gravity.CENTER
        minWidth = context.dp(Dimens.TOUCH_MIN)
        minHeight = context.dp(Dimens.TOUCH_MIN)
        background = context.rounded(colors.photoScrim)
        setOnClickListener { onClick() }
    }
}

private class AtlasCanvas(context: Context) : View(context) {
    private val colors = context.palette
    private val density = resources.displayMetrics.density
    private val scene = SceneCatalog.index(context)
    var onCamera: (AtlasCamera) -> Unit = {}
    var onPick: (String) -> Unit = {}

    private var focus = Box(0f, 0f, 1f, 1f)
    private var courses: List<Pair<Course, AtlasSpot>> = emptyList()
    private var named = false
    private var night = true
    private var shade: AtlasShade? = null
    private var chosen: String? = null
    private var camera = AtlasCamera(0f, 0f, 1f)
    private var ground: Bitmap? = null
    private var detail: Bitmap? = null
    private var animator: ValueAnimator? = null
    private val pinHits = mutableListOf<Pair<RectF, String>>()

    private val picture = Paint(Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val shadePaint = Paint()

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            setCamera(camera.copy(scale = clampScale(camera.scale * detector.scaleFactor, MIN_SCALE, MAX_SCALE)))
            return true
        }
    })
    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val step = camera.scale * density
            setCamera(camera.copy(cx = camera.cx + dx / step, cy = camera.cy + dy / step))
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            pinHits.firstOrNull { it.first.contains(e.x, e.y) }?.let { onPick(it.second) }
            return true
        }
    })

    fun configure(focus: Box, list: List<Course>, named: Boolean, night: Boolean, shade: AtlasShade?) {
        this.focus = focus
        this.named = named
        this.night = night
        this.shade = shade
        courses = list.mapNotNull { course -> scene.courses[course.id]?.let { course to it.atlas } }
        Covers.loadAtlas(context, night) {
            ground = it
            invalidate()
        }
    }

    fun overview(): AtlasCamera {
        val boxes = courses.map { it.second.box }
        return if (boxes.isEmpty()) AtlasCamera(scene.koreaSize.x / 2, scene.koreaSize.y / 2, 1f) else fit(grow(union(boxes), 0.12f, 260f), focus)
    }

    fun cameraFor(courseId: String): AtlasCamera? =
        courses.firstOrNull { it.first.id == courseId }?.let { courseCamera(it.second, focus) }

    fun setChosen(courseId: String?) {
        chosen = courseId
        detail = null
        if (courseId != null) {
            Covers.loadScene(context, courseId, night) {
                if (chosen == courseId) {
                    detail = it
                    invalidate()
                }
            }
        }
        invalidate()
    }

    fun setCamera(next: AtlasCamera) {
        camera = next
        invalidate()
    }

    fun zoom(factor: Float) = moveTo(camera.copy(scale = clampScale(camera.scale * factor, MIN_SCALE, MAX_SCALE)), report = true)

    fun moveTo(target: AtlasCamera, report: Boolean = false) {
        animator?.cancel()
        val start = camera
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MOVE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedFraction
                setCamera(
                    AtlasCamera(
                        start.cx + (target.cx - start.cx) * t,
                        start.cy + (target.cy - start.cy) * t,
                        start.scale + (target.scale - start.scale) * t,
                    ),
                )
            }
            start()
        }
        if (report) onCamera(target)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        animator?.cancel()
        pinch.onTouchEvent(event)
        if (!pinch.isInProgress) taps.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) onCamera(camera)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colors.bgDeep)
        val s = camera.scale
        val origin = toScreen(0f, 0f, camera, focus)
        canvas.save()
        canvas.translate(origin.x * density, origin.y * density)
        canvas.scale(s * density, s * density)
        ground?.let { canvas.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(0f, 0f, scene.koreaSize.x, scene.koreaSize.y), picture) }
        val chosenSpot = courses.firstOrNull { it.first.id == chosen }?.second
        val close = chosenSpot != null && s >= courseCamera(chosenSpot, focus).scale * CLOSE_RATIO
        if (close) drawDetail(canvas, chosenSpot!!)
        drawLines(canvas, s)
        canvas.restore()
        drawLabels(canvas, s, close)
        drawPins(canvas, s, close)
        shade?.let { drawShade(canvas, it) }
    }

    private fun drawDetail(canvas: Canvas, spot: AtlasSpot) {
        val bitmap = detail ?: return
        val f = spot.frame
        val out = RectF(f.x, f.y, f.x + f.w, f.y + f.h)
        val layer = canvas.saveLayer(out, null)
        canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), out, picture)
        val along = floatArrayOf(0.56f, 0.66f, 0.93f, 1f)
        val across = floatArrayOf(0f, 0.12f, 0.86f, 1f)
        val edge = intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT)
        mask.shader = LinearGradient(out.left, 0f, out.right, 0f, edge, along, Shader.TileMode.CLAMP)
        canvas.drawRect(out, mask)
        mask.shader = LinearGradient(0f, out.top, 0f, out.bottom, edge, across, Shader.TileMode.CLAMP)
        canvas.drawRect(out, mask)
        canvas.restoreToCount(layer)
    }

    private fun drawLines(canvas: Canvas, s: Float) {
        courses.forEach { (course, spot) ->
            val dim = chosen != null && chosen != course.id
            spot.lines.forEach { segment ->
                val path = Path()
                segment.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                line.color = colors.photoScrim
                line.alpha = if (dim) 77 else 255
                line.strokeWidth = 10f / s
                canvas.drawPath(path, line)
                line.color = NightPalette.moodColor(segment.mood)
                line.alpha = if (dim) 77 else 255
                line.strokeWidth = 5f / s
                canvas.drawPath(path, line)
            }
        }
    }

    private fun drawLabels(canvas: Canvas, s: Float, close: Boolean) {
        text.textAlign = Paint.Align.CENTER
        text.color = colors.onPhoto
        text.setShadowLayer(8f * density, 0f, density, Color.argb(230, 0, 0, 0))
        scene.labels.forEach { label ->
            val at = toScreen(label.x, label.y, camera, focus)
            val inside = at.x > focus.x - 40 && at.x < focus.x + focus.w + 40 && at.y > focus.y - 40 && at.y < focus.y + focus.h
            val region = label.kind == "region"
            val on = inside && if (region) s < REGION_LABEL_MAX && !close else s >= REGION_LABEL_MAX && s < CITY_LABEL_MAX
            if (on) {
                text.textSize = (if (region) 30f else Dimens.LABEL) * density
                canvas.drawText(label.name, at.x * density, at.y * density + text.textSize * 0.36f, text)
            }
        }
        text.clearShadowLayer()
    }

    private fun drawPins(canvas: Canvas, s: Float, close: Boolean) {
        pinHits.clear()
        val radius = 18f * density
        val pad = 4f * density
        courses.forEachIndexed { i, (course, spot) ->
            val isChosen = course.id == chosen
            if (isChosen && close) return@forEachIndexed
            val at = toScreen(spot.x, spot.y, camera, focus)
            val cx = at.x * density
            val cy = at.y * density
            val alpha = if (chosen != null && !isChosen) 115 else 255
            val showName = named || s >= NAMED_MIN
            text.textAlign = Paint.Align.LEFT
            text.textSize = Dimens.LABEL * density
            val nameWidth = if (showName) text.measureText(course.name) + 12 * density + 8 * density else 0f
            val body = RectF(cx - radius - pad, cy - radius - pad, cx + radius + pad + nameWidth, cy + radius + pad)
            fill.color = colors.photoScrim
            fill.alpha = alpha
            canvas.drawRoundRect(body, body.height() / 2, body.height() / 2, fill)
            if (isChosen) {
                line.color = colors.accent
                line.alpha = 255
                line.strokeWidth = 3 * density
                canvas.drawRoundRect(body, body.height() / 2, body.height() / 2, line)
            }
            fill.color = colors.accent
            fill.alpha = alpha
            canvas.drawCircle(cx, cy, radius, fill)
            text.textAlign = Paint.Align.CENTER
            text.color = colors.onAccent
            text.alpha = alpha
            canvas.drawText("${i + 1}", cx, cy + text.textSize * 0.36f, text)
            if (showName) {
                text.textAlign = Paint.Align.LEFT
                text.color = colors.onPhoto
                text.alpha = alpha
                canvas.drawText(course.name, cx + radius + 12 * density, cy + text.textSize * 0.36f, text)
            }
            val touch = 16 * density
            pinHits += RectF(body.left - touch, body.top - touch, body.right + touch, body.bottom + touch) to course.id
        }
        pinHits.reverse()
    }

    private fun drawShade(canvas: Canvas, shade: AtlasShade) {
        val w = width.toFloat()
        val solid = shade.solid * density
        val clear = shade.clear * density
        val base = colors.bg
        val none = Color.argb(0, Color.red(base), Color.green(base), Color.blue(base))
        shadePaint.shader = if (shade.fromRight) {
            LinearGradient(w - solid, 0f, w - clear, 0f, base, none, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(solid, 0f, clear, 0f, base, none, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, height.toFloat(), shadePaint)
    }
}
