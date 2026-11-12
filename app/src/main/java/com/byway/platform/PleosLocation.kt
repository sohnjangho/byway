package com.byway.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byway.core.geo.GeoPoint

/** 현재 위치를 일정 간격으로 읽어 메인 스레드로 넘긴다. */
class PleosLocation(context: Context, private val onPoint: (GeoPoint) -> Unit) {
    private val fused = FusedLocation(context)
    private val handler = Handler(Looper.getMainLooper())
    private var last: GeoPoint? = null

    private val reader = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val point = location?.let { GeoPoint(it.latitude, it.longitude) } ?: return
            handler.post {
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
