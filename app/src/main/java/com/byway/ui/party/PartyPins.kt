package com.byway.ui.party

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.byway.core.geo.GeoPoint

/** 지도에 찍을 파티 멤버 하나. [avatar]는 앱에 든 그림 이름이고, [name]은 확대 지도에서 핀 아래에 적는 닉네임이다. */
data class MapPin(val id: String, val point: GeoPoint, val avatar: String, val name: String = "")

/** 물방울 핀의 자리. 끝([tipX], [tipY])이 위치를 가리키고, 아바타 원은 그 위에 뜬다. */
data class PinGeometry(val centerX: Float, val centerY: Float, val avatarRadius: Float, val outerRadius: Float)

private const val RING = 0.18f
private const val LIFT = 1.9f

fun pinGeometry(tipX: Float, tipY: Float, radius: Float): PinGeometry {
    val outer = radius * (1 + RING)
    return PinGeometry(tipX, tipY - outer * LIFT, radius, outer)
}

/**
 * 프로필 그림을 원형으로 잘라 물방울 핀에 넣어 그린다(공유 설계 6-5).
 * 움직임 없이 그 자리에 그린다. 주행 화면 규칙(움직이는 것은 위치 점뿐)을 지킨다.
 */
internal object PartyPins {
    private const val FOLDER = "avatars"
    private val cache = mutableMapOf<String, Bitmap?>()
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    fun avatar(context: Context, name: String): Bitmap? = cache.getOrPut(name) {
        runCatching { context.assets.open("$FOLDER/$name.png").use(BitmapFactory::decodeStream) }.getOrNull()
    }

    fun draw(canvas: Canvas, context: Context, tipX: Float, tipY: Float, avatar: String, radius: Float, ring: Int, shadow: Int) {
        val pin = pinGeometry(tipX, tipY, radius)
        val drop = Path().apply {
            addCircle(pin.centerX, pin.centerY, pin.outerRadius, Path.Direction.CW)
            moveTo(pin.centerX - pin.outerRadius * 0.62f, pin.centerY + pin.outerRadius * 0.78f)
            lineTo(tipX, tipY)
            lineTo(pin.centerX + pin.outerRadius * 0.62f, pin.centerY + pin.outerRadius * 0.78f)
            close()
        }
        body.color = shadow
        canvas.save()
        canvas.translate(0f, radius * 0.08f)
        canvas.drawPath(drop, body)
        canvas.restore()
        body.color = ring
        canvas.drawPath(drop, body)
        drawCircle(canvas, context, pin.centerX, pin.centerY, avatar, pin.avatarRadius)
    }

    /** 아바타 그림을 원형으로 잘라 그린다. 그림이 없으면 아무것도 그리지 않는다. */
    fun drawCircle(canvas: Canvas, context: Context, cx: Float, cy: Float, avatar: String, radius: Float) {
        val bitmap = avatar(context, avatar) ?: return
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = 2 * radius / minOf(bitmap.width, bitmap.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate(cx - bitmap.width * scale / 2, cy - bitmap.height * scale / 2)
        shader.setLocalMatrix(matrix)
        face.shader = shader
        canvas.drawCircle(cx, cy, radius, face)
    }
}
