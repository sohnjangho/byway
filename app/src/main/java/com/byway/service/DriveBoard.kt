package com.byway.service

import android.os.Handler
import android.os.Looper
import com.byway.core.course.Course
import com.byway.core.geo.GeoPoint

/** 화면이 그리는 주행 상태. 화면은 이것만 안다. */
data class DriveState(
    val running: Boolean = false,
    val course: Course? = null,
    val line: String = "",
    val spoken: String = "",
    val cabin: String = "",
    val position: GeoPoint? = null,
)

object DriveBoard {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(DriveState) -> Unit>()

    var state = DriveState()
        private set

    fun update(change: (DriveState) -> DriveState) {
        state = change(state)
        main.post { listeners.toList().forEach { it(state) } }
    }

    fun listen(listener: (DriveState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun stopListening(listener: (DriveState) -> Unit) {
        listeners -= listener
    }
}
