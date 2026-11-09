package com.byway.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import com.byway.core.course.MoodLook
import com.byway.core.course.SceneMood
import com.byway.service.SceneCatalog
import kotlin.math.sin
import kotlin.random.Random

private const val TILE_WIDTH = 480
private const val TILE_HEIGHT = 640
private const val FAR_ALPHA = 0.7f
private const val FAR_OFFSET_X = 160f
private const val FAR_OFFSET_Y = 220f
private const val WASH_STOP = 0.72f
private const val SIDE_SOLID = 0.62f
private const val TOP_FADE = 0.22f
private const val TWINKLE_LOW = 0.35f

/** 정차 화면 정보 칸의 분위기 움직임. 지도와 경로에는 그리지 않고 주행 화면에는 쓰지 않는다. */
@SuppressLint("ViewConstructor")
internal class MoodEffectView(context: Context, private val below: Boolean) : View(context) {
    private var look: MoodLook? = null
    private var near: Bitmap? = null
    private var far: Bitmap? = null
    private var twinkle = false
    private var wash = Color.TRANSPARENT
    private var running = false
    private val started = SystemClock.uptimeMillis()
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val washPaint = Paint()
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val matrixNear = Matrix()

    fun show(mood: SceneMood, night: Boolean) {
        val next = moodKey(mood)?.let { SceneCatalog.index(context).moods[it] }
        look = next
        visibility = if (next == null) GONE else VISIBLE
        if (next == null) return
        val color = parseRgba(if (night) next.night else next.day)
        wash = parseRgba(next.wash)
        twinkle = mood == SceneMood.NIGHT
        near = tile(next, color, 1)
        far = tile(next, color, 2)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        invalidate()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val current = look ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val moving = ValueAnimator.areAnimatorsEnabled()
        val seconds = if (moving) (SystemClock.uptimeMillis() - started) / 1000f else 0f
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        washPaint.shader = LinearGradient(0f, 0f, 0f, h * WASH_STOP, wash, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, washPaint)
        near?.let { drawTile(canvas, it, current, seconds, 0, 0f, 0f, 1f) }
        far?.let { drawTile(canvas, it, current, seconds, 1, FAR_OFFSET_X, FAR_OFFSET_Y, FAR_ALPHA) }
        maskPaint.shader = if (below) {
            LinearGradient(0f, 0f, 0f, h * TOP_FADE, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(w * SIDE_SOLID, 0f, w, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, maskPaint)
        canvas.restoreToCount(layer)
        if (running && moving) postInvalidateOnAnimation()
    }

    private fun drawTile(canvas: Canvas, tile: Bitmap, current: MoodLook, seconds: Float, index: Int, offsetX: Float, offsetY: Float, opacity: Float) {
        val shift = tileShift(current, seconds, index)
        val density = resources.displayMetrics.density
        matrixNear.setTranslate((offsetX + shift.x) * density, (offsetY + shift.y) * density)
        val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(matrixNear) }
        tilePaint.shader = shader
        val blink = if (twinkle) TWINKLE_LOW + (1 - TWINKLE_LOW) * (0.5f + 0.5f * sin(seconds * (index + 1) * 1.2f)) else 1f
        tilePaint.alpha = (opacity * blink * 255).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tilePaint)
    }

    private fun tile(current: MoodLook, color: Int, seed: Int): Bitmap {
        val density = resources.displayMetrics.density
        val w = (TILE_WIDTH * density).toInt()
        val h = (TILE_HEIGHT * density).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val random = Random(current.shape.hashCode() + seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        repeat(current.count) { drawShape(canvas, paint, current.shape, color, random) }
        return bitmap
    }

    private fun drawShape(canvas: Canvas, paint: Paint, shape: String, color: Int, r: Random) {
        fun x(margin: Int) = r.nextInt(margin, TILE_WIDTH - margin + 1).toFloat()
        fun y(margin: Int) = r.nextInt(margin, TILE_HEIGHT - margin + 1).toFloat()
        fun range(low: Float, high: Float) = low + r.nextFloat() * (high - low)
        fun opacity(low: Float, high: Float) { paint.alpha = (range(low, high) * 255).toInt() }
        paint.style = Paint.Style.FILL
        when (shape) {
            "flake" -> { opacity(0.5f, 0.95f); canvas.drawCircle(x(8), y(8), range(1.5f, 4.5f), paint) }
            "star" -> { opacity(0.4f, 1f); canvas.drawCircle(x(6), y(6), range(0.8f, 2.2f), paint) }
            "mote" -> { opacity(0.3f, 0.8f); canvas.drawCircle(x(8), y(8), range(1.5f, 3.5f), paint) }
            "streak" -> {
                val startX = r.nextInt(30, TILE_WIDTH - 9).toFloat()
                val startY = r.nextInt(10, TILE_HEIGHT - 69).toFloat()
                val length = r.nextInt(26, 59).toFloat()
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = range(1.2f, 2.2f)
                opacity(0.35f, 0.8f)
                canvas.drawLine(startX, startY, startX - length * 0.22f, startY + length, paint)
            }
            "petal" -> {
                val cx = x(14)
                val cy = y(14)
                val rx = range(5f, 9f)
                val ry = range(3f, 5f)
                opacity(0.6f, 0.95f)
                canvas.save()
                canvas.rotate(r.nextInt(0, 181).toFloat(), cx, cy)
                canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
                canvas.restore()
            }
            "leaf" -> {
                val size = range(0.8f, 1.5f)
                val path = Path().apply {
                    moveTo(0f, -12f)
                    cubicTo(7f, -8f, 9f, 0f, 0f, 12f)
                    cubicTo(-9f, 0f, -7f, -8f, 0f, -12f)
                    close()
                }
                // 글 칸 뒤로 지나가므로 글자가 먼저 읽히게 옅게 둔다.
                opacity(0.2f, 0.4f)
                canvas.save()
                canvas.translate(x(24), y(24))
                canvas.rotate(r.nextInt(0, 360).toFloat())
                canvas.scale(size, size)
                canvas.drawPath(path, paint)
                canvas.restore()
            }
            "crystal" -> {
                val size = range(5f, 10f)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 1.4f
                opacity(0.5f, 0.9f)
                canvas.save()
                canvas.translate(x(16), y(16))
                repeat(6) {
                    canvas.drawLine(0f, 0f, 0f, -size, paint)
                    canvas.rotate(60f)
                }
                canvas.restore()
            }
            "mist" -> drawMist(canvas, color, r)
        }
    }

    /** 가장자리가 사라지는 안개. 가로로 흐르므로 양옆에도 그려 타일 경계를 감춘다. */
    private fun drawMist(canvas: Canvas, color: Int, r: Random) {
        val ry = r.nextInt(70, 131).toFloat()
        val rx = r.nextInt(200, 321).toFloat()
        val x = r.nextInt(0, TILE_WIDTH + 1).toFloat()
        val y = r.nextInt(ry.toInt(), TILE_HEIGHT - ry.toInt() + 1).toFloat()
        val strength = 0.34f * (0.5f + r.nextFloat() * 0.45f)
        val center = Color.argb((strength * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        val edge = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 1f, center, edge, Shader.TileMode.CLAMP) }
        listOf(-TILE_WIDTH.toFloat(), 0f, TILE_WIDTH.toFloat()).forEach { shift ->
            canvas.save()
            canvas.translate(x + shift, y)
            canvas.scale(rx, ry)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
    }
}
