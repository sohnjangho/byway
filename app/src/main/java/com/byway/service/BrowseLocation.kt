package com.byway.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.byway.core.geo.GeoPoint
import com.byway.platform.PleosLocation

/**
 * 더 많은 코스의 "내 주변"에 쓰는 위치. 위치는 원래 코스 주행·길 기록 중에만 받으므로 이 화면이 보이는 동안에만 따로 받는다
 * (더 많은 코스 설계 5절). 화면을 떠나거나 앱이 멈추면 끈다.
 */
object BrowseLocation {
    /** 처음이거나 이만큼 넘게 움직였을 때만 화면을 다시 그린다. 위치는 1초마다 온다. */
    private const val TELL_METERS = 1_000.0

    /** 앱이 출발·기록 때 묻는 위치 권한과 같다. */
    private const val LOCATION = "pleos.car.permission.FUSED_LOCATION"

    var last: GeoPoint? = null
        private set

    /** [last]를 받은 시각(elapsedRealtime). Gleo 음성 찾기가 오래된 자리를 쓰지 않게 한다(검토 I4). */
    var lastAt: Long? = null
        private set
    private var told: GeoPoint? = null
    private var reader: PleosLocation? = null
    private var onMoved: () -> Unit = {}

    /** 화면이 보이고, 위치 권한이 있고, 아직 받고 있지 않을 때만 시작한다(검토 I-3). */
    internal fun shouldStart(on: Boolean, granted: Boolean, running: Boolean): Boolean = on && granted && !running

    internal fun shouldTell(before: GeoPoint?, now: GeoPoint): Boolean = before == null || before.distanceTo(now) > TELL_METERS

    /** [on]이면 위치를 받기 시작하고, 아니면 끈다. 렌더마다 불러도 된다. */
    fun keep(context: Context, on: Boolean, moved: () -> Unit) {
        onMoved = moved
        if (!on) return stop()
        val granted = context.checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!shouldStart(on, granted, reader != null)) return
        reader = try {
            PleosLocation(context.applicationContext) { point ->
                last = point
                lastAt = android.os.SystemClock.elapsedRealtime()
                if (shouldTell(told, point)) {
                    told = point
                    onMoved()
                }
            }.also { it.start() }
        } catch (e: RuntimeException) {
            // 권한이 없거나 위치 서비스가 없으면 내 주변 칩을 흐리게 둔 채로 쓴다.
            Log.w("BROWSE", "location unavailable $e")
            null
        }
    }

    fun stop() {
        reader?.release()
        reader = null
    }
}
