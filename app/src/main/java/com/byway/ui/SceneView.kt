package com.byway.ui

import com.byway.core.course.scenePointOf
import com.byway.ui.party.MapPin
import com.byway.ui.party.PartyPins
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.byway.core.course.Course
import com.byway.core.course.CourseScene
import com.byway.core.course.Px
import com.byway.core.course.ScenePlace
import com.byway.core.geo.GeoPoint
import com.byway.service.SceneCatalog
import kotlin.math.abs
import kotlin.math.sign

private const val NIGHT_OPACITY = 0.66f
private const val DAY_OPACITY = 0.86f
private const val PIN_RADIUS = 20f
private const val GLOW_ALPHA = 0.22f
private const val MARKER_HALO = 30
private const val MARKER_HALO_ALPHA = 80
private const val MARKER_DOT = 12
private const val MARKER_RING = 4
private const val SWIPE_DISTANCE_DP = 420
private const val FLING_FACTOR = 6f
private const val PICTURE_MIX_SPEED = 1.6f

/** 경로는 한눈에 끝나지 않고 앞에서부터 그려져 보이도록 천천히 시작한다. */
private val routeEase = PathInterpolator(0.33f, 0f, 0.2f, 1f)

/** 코스 둘레 지도 그림(일반 지도나 위성)과 경로. 밀면 풍경 사진으로 바뀐다. 그림이 없으면 코스 그림으로 대신한다. */
@SuppressLint("ViewConstructor")
internal class SceneView(
    context: Context,
    private val window: WindowClass,
    list: Boolean = false,
    /** 풍경으로 넘기는 단추를 지도 위에 둘지. 넓은 창의 홈은 버튼 줄에 따로 둔다. */
    private val hintOnMap: Boolean = true,
    /** 1/3 창에서 지도 아래쪽에 훑어보기 사진 띠가 놓이는지. 경로가 띠 뒤로 들어가지 않게 장면을 줄인다. */
    strip: Boolean = false,
) : FrameLayout(context) {
    /** 풍경 사진이 보이는지 바뀔 때 부른다. */
    var onPhoto: (Boolean) -> Unit = {}

    private val colors = context.palette
    private val canvasView = SceneCanvas(context, window, list, strip)
    private val fallback = CoursePathView(context)
    private val photo = ScenePhoto(context, window).apply { visibility = GONE }
    private val hint = chip("")
    private val credit = creditChip(mapCreditLine(currentMapStyle()))
    private var mapText = mapCreditLine(currentMapStyle())
    private var backdropText: String? = null
    private var photoText: String? = null
    private val latest = LatestOnly()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * SWIPE_FACTOR
    private val fling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * FLING_FACTOR
    private val swipeDistance = context.dp(SWIPE_DISTANCE_DP).toFloat()
    private var downX = 0f
    private var course: Course? = null
    private var canShowPhoto = false
    private var photoShown = false
    private var progress = 0f
    private var side = 1f
    private var dragging = false
    private var tracker: VelocityTracker? = null
    private var settle: ValueAnimator? = null

    init {
        addView(fallback, matchFrame())
        addView(canvasView, matchFrame())
        addView(photo, matchFrame())
        val edge = context.dp(Dimens.SPACE_8)
        val third = window == WindowClass.THIRD
        // 풍경 단추는 운전석에서 가까운 왼쪽 아래에 둔다. 넓은 창은 글 칸 바로 옆이다.
        val hintSize = LayoutParams(WRAP, context.dp(Dimens.TOUCH_MIN), Gravity.BOTTOM or Gravity.START).apply {
            val sideEnd = Dimens.RAIL_WIDTH + if (window == WindowClass.FULL) Dimens.SIDE_WIDTH_FULL else Dimens.SIDE_WIDTH
            marginStart = context.dp(if (third) Dimens.SPACE_6 else sideEnd + Dimens.SPACE_6)
            // 1/3 창의 지도 아래쪽에 훑어보기 띠가 놓이면 그 위에 둔다.
            val thirdBottom = if (strip) ScrubStrip.HEIGHT + Dimens.SPACE_6 else Dimens.SPACE_3
            // 목록 화면은 오른쪽 아래 출처가 길게 놓이므로 그 위에 둔다.
            bottomMargin = context.dp(if (third) thirdBottom else if (list) Dimens.LIST_HINT_BOTTOM else Dimens.SCENE_CREDIT_BOTTOM)
        }
        addView(hint, hintSize)
        // 출처는 작은 글로 지도 모서리에 둔다(화면 다듬기 설계 5절). 넓은 창은 글 칸 아래까지 뻗지 않게 폭을 묶는다.
        // 사진 띠가 있는 1/3 홈은 줄인 장면의 왼쪽(경로가 오지 않는 자리)에 좁게 세워 경로와 번호를 가리지 않는다.
        credit.maxWidth = when {
            third && strip -> context.dp(Dimens.THIRD_CREDIT_WIDTH)
            third -> Int.MAX_VALUE
            else -> context.dp(Dimens.SCENE_CREDIT_WIDTH)
        }
        val creditSize = {
            if (third && strip) {
                LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
                    topMargin = context.dp(Dimens.RAIL_TOP_HEIGHT + Dimens.SPACE_2)
                    marginStart = context.dp(Dimens.SPACE_3)
                }
            } else if (third) {
                LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                    bottomMargin = context.dp(Dimens.SPACE_3)
                    marginStart = context.dp(Dimens.SPACE_3)
                    marginEnd = context.dp(Dimens.SPACE_3)
                }
            } else {
                // 전체·2/3 창은 아래에 코스 띠가 있어 그 위에 둔다.
                LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                    bottomMargin = context.dp(if (list) Dimens.SPACE_3 else Dimens.SCENE_CREDIT_BOTTOM)
                    marginEnd = edge
                }
            }
        }
        addView(credit, creditSize())
        hint.pressMotion()
        hint.setOnClickListener { togglePhoto() }
    }

    /** 출처 칩. 가장 작은 글(Dimens.CREDIT)에 여백도 좁혀 그림을 덜 가린다. */
    private fun creditChip(text: String): TextView = context.label(text, Dimens.CREDIT, colors.onPhoto).apply {
        background = context.rounded(colors.photoScrim, Dimens.RADIUS_SM)
        val inset = context.dp(Dimens.CREDIT_PADDING)
        setPadding(context.dp(Dimens.SPACE_2), inset, context.dp(Dimens.SPACE_2), inset)
    }

    private fun chip(text: String): TextView = context.label(text, Dimens.LABEL, colors.onPhoto).apply {
        background = context.rounded(colors.photoScrim, Dimens.RADIUS_SM)
        gravity = Gravity.CENTER
        val inset = context.dp(Dimens.CREDIT_PADDING)
        setPadding(context.dp(Dimens.SPACE_3), inset, context.dp(Dimens.SPACE_3), inset)
    }

    /** [reveal]이면 이전 그림에서 넘어오며 경로를 앞에서부터 그린다. */
    fun show(course: Course, scene: CourseScene?, places: List<ScenePlace>, night: Boolean, sceneryAvailable: Boolean, reveal: Boolean = false) {
        this.course = course
        shownScene = scene
        canShowPhoto = sceneryAvailable
        showRoute()
        fallback.show(course, emptyList())
        val token = latest.next()
        canvasView.showScene(scene, places, night, SceneCatalog.index(context).sceneSize.x, reveal)
        val hasPicture = scene != null && Covers.hasScene(context, course.id, night)
        canvasView.visibility = if (hasPicture) VISIBLE else GONE
        fallback.visibility = if (hasPicture) GONE else VISIBLE
        if (hasPicture) {
            Covers.loadScene(context, course.id, night) { bitmap ->
                if (latest.isCurrent(token)) canvasView.setPicture(bitmap)
            }
        }
        photo.setPicture(null)
        if (sceneryAvailable) {
            Covers.loadScenery(context, course) { if (latest.isCurrent(token)) photo.setPicture(it) }
            photoText = photoCreditLine(course.sceneryCredit)
        }
    }

    private fun showRoute() {
        settle?.cancel()
        progress = 0f
        placePhoto()
        markChips(false)
    }

    private fun markChips(onPhoto: Boolean) {
        photoShown = onPhoto
        credit.text = creditText(mapText, backdropText, photoText, onPhoto)
        hint.text = if (onPhoto) "경로로 돌아가기" else "밀어서 풍경 보기"
        hint.visibility = if (canShowPhoto && hintOnMap) VISIBLE else GONE
        this.onPhoto(onPhoto)
    }

    /** 사진은 손가락을 따라 옆에서 들어오고 나간다. */
    private fun placePhoto() {
        photo.visibility = if (progress > 0f) VISIBLE else GONE
        photo.alpha = progress
        photo.translationX = side * (1f - progress) * swipeDistance
    }

    /** 훑어보기에서 고른 지점. 장면 그림 안의 좌표다. */
    fun setMarker(at: Px?) = canvasView.setMarker(at)

    /** 지도 그림이 없어 코스 그림으로 대신할 때의 위치 점. */
    fun moveFallbackTo(point: GeoPoint?) = fallback.moveTo(point)

    private var shownScene: CourseScene? = null

    /** 파티 멤버 핀. 장면 그림에는 장면 좌표로, 코스 선 그림에는 위경도로 찍는다. */
    fun setPins(pins: List<MapPin>) {
        fallback.setPins(pins)
        val frame = shownScene?.atlas?.frame ?: return canvasView.setPins(emptyList())
        val size = SceneCatalog.index(context).sceneSize
        canvasView.setPins(pins.map { scenePointOf(it.point, frame, size) to it.avatar })
    }

    /** 화면에 함께 보이는 지도의 출처. */
    fun setCredit(text: String) {
        mapText = text
        credit.text = creditText(mapText, backdropText, photoText, photoShown)
    }

    /** 글 칸 뒤에 흐린 코스 사진을 깔면 그 출처(변형 포함). 없으면 null. */
    fun setBackdropCredit(text: String?) {
        backdropText = text
        credit.text = creditText(mapText, backdropText, photoText, photoShown)
    }

    fun togglePhoto() {
        if (!canShowPhoto) return
        side = 1f
        settleTo(!photoShown)
    }

    private fun settleTo(onPhoto: Boolean) {
        settle?.cancel()
        markChips(onPhoto)
        settle = ValueAnimator.ofFloat(progress, if (onPhoto) 1f else 0f).apply {
            duration = MotionSpec.MEDIUM2
            interpolator = motionEase
            addUpdateListener {
                progress = it.animatedValue as Float
                placePhoto()
            }
            start()
        }
    }

    private fun followFinger(drag: Float) {
        settle?.cancel()
        progress = swipeProgress(photoShown, drag, swipeDistance)
        side = if (photoShown) sign(drag) else -sign(drag)
        placePhoto()
    }

    private fun release(drag: Float) {
        val speed = tracker?.run {
            computeCurrentVelocity(1000)
            xVelocity
        } ?: 0f
        val along = speed * sign(drag)
        settleTo(settlesOnPhoto(progress, if (photoShown) -along else along, fling))
    }

    private fun endGesture() {
        dragging = false
        tracker?.recycle()
        tracker = null
    }

    override fun onDetachedFromWindow() {
        settle?.cancel()
        endGesture()
        super.onDetachedFromWindow()
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
            MotionEvent.ACTION_MOVE -> if (canShowPhoto) {
                val tracking = tracker ?: VelocityTracker.obtain().also { tracker = it }
                tracking.addMovement(event)
                val drag = event.x - downX
                if (abs(drag) > slop) dragging = true
                if (dragging) followFinger(drag - sign(drag) * slop)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) release(event.x - downX)
                endGesture()
            }
        }
        return canShowPhoto
    }

    private companion object {
        const val SWIPE_FACTOR = 4
    }
}

/** 풍경 사진. 정보 칸 아래쪽은 흐려서 글이 사진 위에 그대로 놓이지 않게 한다. */
private class ScenePhoto(context: Context, private val window: WindowClass) : View(context) {
    private var picture: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    fun setPicture(bitmap: Bitmap?) {
        picture = bitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = picture ?: return
        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawBitmap(bitmap, centerCrop(bitmap, width, height), Rect(0, 0, width, height), paint)
        val band = photoFade(window, h / density)
        val from = band.from * density
        val to = band.to * density
        val start = android.graphics.Color.argb((band.alphaFrom * 255).toInt(), 0, 0, 0)
        mask.shader = if (band.vertical) {
            LinearGradient(0f, from, 0f, to, start, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(from, 0f, to, 0f, start, android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, mask)
        canvas.restoreToCount(layer)
    }
}

/** [width]x[height] 칸을 빈틈없이 채우도록 그림 가운데를 잘라 낼 범위. */
internal fun centerCrop(bitmap: Bitmap, width: Int, height: Int): Rect {
    val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
    val w = (width / scale).toInt()
    val h = (height / scale).toInt()
    val left = (bitmap.width - w) / 2
    val top = (bitmap.height - h) / 2
    return Rect(left, top, left + w, top + h)
}

/** 그림은 그대로 두고 경로는 그 위에 그린다. 지도 라이브러리와 네트워크가 필요 없다. */
private class SceneCanvas(
    context: Context,
    private val window: WindowClass,
    private val list: Boolean,
    private val strip: Boolean,
) : View(context) {
    private val colors = context.palette
    private var picture: Bitmap? = null
    private var scene: CourseScene? = null
    private var places: List<ScenePlace> = emptyList()
    private var night = true
    private var sceneWidth = 0f
    private var outgoing: Bitmap? = null
    private var revealing = false
    private var reveal = 1f
    private var mix = 1f
    private var mover: ValueAnimator? = null
    private var marker: Px? = null
    private var pins: List<Pair<Px, String>> = emptyList()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val layerPaint = Paint()
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

    fun showScene(scene: CourseScene?, places: List<ScenePlace>, night: Boolean, sceneWidth: Float, reveal: Boolean) {
        mover?.cancel()
        this.scene = scene
        this.places = places
        this.night = night
        this.sceneWidth = sceneWidth
        revealing = reveal
        outgoing = if (reveal) picture ?: outgoing else null
        picture = null
        this.reveal = if (reveal) 0f else 1f
        mix = if (reveal) 0f else 1f
        invalidate()
    }

    fun setMarker(at: Px?) {
        if (marker == at) return
        marker = at
        invalidate()
    }

    fun setPins(next: List<Pair<Px, String>>) {
        if (pins == next) return
        pins = next
        invalidate()
    }

    fun setPicture(bitmap: Bitmap) {
        picture = bitmap
        invalidate()
        if (!revealing) return
        // 넘어올 이전 그림이 없으면 화면 전환이 이미 그림을 드러내고 있다.
        if (outgoing == null) mix = 1f
        mover = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionSpec.LONG2
            interpolator = routeEase
            addUpdateListener {
                reveal = it.animatedFraction
                if (outgoing != null) mix = (reveal * PICTURE_MIX_SPEED).coerceAtMost(1f)
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        mover?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (picture == null && outgoing == null) return
        val density = resources.displayMetrics.density
        val rect = sceneRect(window, width / density, height / density, list, strip)
        val out = RectF(rect.left * density, rect.top * density, (rect.left + rect.width) * density, (rect.top + rect.height) * density)
        drawPicture(canvas, out, density)
        if (picture != null) scene?.let { drawRoute(canvas, it, out) }
    }

    private fun drawPicture(canvas: Canvas, out: RectF, density: Float) {
        layerPaint.alpha = ((if (night) NIGHT_OPACITY else DAY_OPACITY) * 255).toInt()
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)
        if (mix < 1f) {
            outgoing?.let {
                paint.alpha = 255
                canvas.drawBitmap(it, Rect(0, 0, it.width, it.height), out, paint)
            }
        }
        picture?.let {
            paint.alpha = (mix * 255).toInt()
            canvas.drawBitmap(it, Rect(0, 0, it.width, it.height), out, paint)
        }
        val band = sceneFade(window, list, strip)
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
        val whole = scene.lines.map { pathOf(it.points.map { p -> Pair(p.x, p.y) }, out, scale) }
        val shown = if (reveal < 1f) trimmed(whole) else whole
        val paths = scene.lines.zip(shown) { sceneLine, path -> sceneLine.mood to path }
        paths.forEach { (mood, path) ->
            stroke.color = NightPalette.moodColor(mood)
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
            stroke.color = NightPalette.moodColor(mood)
            stroke.alpha = 255
            stroke.strokeWidth = line
            canvas.drawPath(path, stroke)
        }
        places.forEach { place -> drawPin(canvas, out.left + place.x * scale, out.top + place.y * scale, place.no, pinAlpha(reveal)) }
        marker?.let { drawMarker(canvas, out.left + it.x * scale, out.top + it.y * scale) }
        pins.forEach { (at, avatar) ->
            PartyPins.draw(canvas, context, out.left + at.x * scale, out.top + at.y * scale, avatar, context.dp(PIN_RADIUS_DP.toInt()).toFloat(), colors.onPhoto, colors.photoScrim)
        }
    }

    /** 훑어보기에서 고른 지점. 번호 표시와 헷갈리지 않게 주황 점으로 그린다. */
    private fun drawMarker(canvas: Canvas, x: Float, y: Float) {
        pinFill.color = NightPalette.accent
        pinFill.alpha = MARKER_HALO_ALPHA
        canvas.drawCircle(x, y, context.dp(MARKER_HALO).toFloat(), pinFill)
        pinFill.color = colors.onPhoto
        canvas.drawCircle(x, y, context.dp(MARKER_DOT + MARKER_RING).toFloat(), pinFill)
        pinFill.color = NightPalette.accent
        canvas.drawCircle(x, y, context.dp(MARKER_DOT).toFloat(), pinFill)
    }

    private fun trimmed(paths: List<Path>): List<Path> {
        val lengths = revealedLengths(paths.map { PathMeasure(it, false).length }, reveal)
        return paths.zip(lengths) { path, length ->
            Path().also { PathMeasure(path, false).getSegment(0f, length, it, true) }
        }
    }

    private fun pathOf(points: List<Pair<Float, Float>>, out: RectF, scale: Float): Path = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(out.left + x * scale, out.top + y * scale) else lineTo(out.left + x * scale, out.top + y * scale)
        }
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, number: Int, opacity: Float) {
        if (opacity <= 0f) return
        val radius = context.dp(PIN_RADIUS.toInt()).toFloat()
        val dark = darkPinsOn(currentMapStyle(), night)
        val face = if (dark) colors.ink else colors.onPhoto
        val edge = if (dark) colors.bg else colors.photoScrim
        pinFill.color = edge
        pinFill.alpha = (pinFill.alpha * opacity).toInt()
        canvas.drawCircle(x, y, radius + context.dp(3), pinFill)
        pinFill.color = face
        pinFill.alpha = (pinFill.alpha * opacity).toInt()
        canvas.drawCircle(x, y, radius, pinFill)
        pinText.color = edge
        pinText.alpha = (pinText.alpha * opacity).toInt()
        pinText.textSize = context.dp(Dimens.LABEL.toInt()).toFloat()
        canvas.drawText("$number", x, y + pinText.textSize * 0.36f, pinText)
    }
}
