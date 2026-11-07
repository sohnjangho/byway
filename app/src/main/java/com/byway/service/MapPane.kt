package com.byway.service

import android.content.Context
import android.util.Log
import android.view.View
import com.byway.core.course.Course
import com.byway.platform.CourseMapPane

/** 출처 문구는 타일 출처와 한 몸이라 여기 둔다. 화면에 그리는 일은 화면이 맡는다. */
enum class MapStyle(val credit: String) {
    STREET("OpenFreeMap © OpenMapTiles Data from OpenStreetMap"),
    SATELLITE("Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"),
}

/** 지도 위 코스 색. 값은 화면이 토큰에서 고른다. */
data class MapColors(val forest: Int, val city: Int, val river: Int, val outline: Int, val blank: Int)

/** 코스 상세의 지도 칸. 지도 라이브러리 타입을 화면에 내보내지 않는다. 정차 중에만 쓴다. */
interface MapPane {
    val view: View
    fun show(course: Course, style: MapStyle)
    fun release()
}

object MapPanes {
    /** 지도를 쓸 수 없으면 null 이다. 화면은 코스 그림으로 대신한다. */
    fun create(context: Context, colors: MapColors): MapPane? =
        runCatching { CourseMapPane(context, colors) }.onFailure { Log.w("DRIVE", "map unavailable", it) }.getOrNull()
}
