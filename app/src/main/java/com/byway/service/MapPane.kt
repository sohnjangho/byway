package com.byway.service

import android.content.Context
import android.util.Log
import android.view.View
import com.byway.core.course.Course
import com.byway.platform.CourseMapPane

enum class MapStyle { STREET, SATELLITE }

/** 코스 상세의 지도 칸. 지도 라이브러리 타입을 화면에 내보내지 않는다. 정차 중에만 쓴다. */
interface MapPane {
    val view: View
    fun show(course: Course, style: MapStyle)
    fun release()
}

object MapPanes {
    /** 지도를 쓸 수 없으면 null 이다. 화면은 코스 그림으로 대신한다. */
    fun create(context: Context): MapPane? =
        runCatching { CourseMapPane(context) }.onFailure { Log.w("DRIVE", "map unavailable", it) }.getOrNull()
}
