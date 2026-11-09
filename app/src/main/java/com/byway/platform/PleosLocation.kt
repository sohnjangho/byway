package com.byway.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.byway.core.geo.GeoPoint

/** 현재 위치를 일정 간격으로 읽어 메인 스레드로 넘긴다. */
class PleosLocation(context: Context, private val onPoint: (GeoPoint) -> Unit) {
    private val fused = FusedLocation(context)
    private val handler = Handler(Looper.getMainLooper())
    private var last: GeoPoint? = null
    private var stamp = 0L
    private var stampAt = 0L

    private val reader = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val point = location?.let { GeoPoint(it.latitude, it.longitude) } ?: return
            val time = location.timestampMillis
            handler.post {
                if (time != stamp) {
                    stamp = time
                    stampAt = SystemClock.elapsedRealtime()
                }
                if (point != last) {
                    last = point
                    onPoint(point)
                }
            }
        }
        override fun onError(exception: Exception) { Log.w("DRIVE", "location error $exception") }
    }

    private val poll = object : Runnable {
        override fun run() {
            fused.getLastKnownLocation(reader)
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    /** 위치의 시각이 마지막으로 바뀐 뒤 지난 시간. 차가 서 있어도 위치가 살아 있으면 시각은 바뀐다. 받은 적이 없으면 null 이다. */
    fun fixAgeMs(): Long? = if (stampAt == 0L) null else SystemClock.elapsedRealtime() - stampAt

    fun start() {
        fused.initialize()
        handler.postDelayed(poll, INTERVAL_MS)
    }

    fun release() {
        handler.removeCallbacks(poll)
        fused.release()
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
    }
}
