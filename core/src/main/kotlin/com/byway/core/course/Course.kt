package com.byway.core.course

import com.byway.core.geo.GeoPoint

/** 코스의 이름 붙은 구간. 인덱스는 [Course.path] 기준이고 양 끝을 포함한다. */
data class Section(
    val name: String,
    val startIndex: Int,
    val endIndex: Int,
    val mood: String? = null,
)

data class Course(
    val id: String,
    val name: String,
    val path: List<GeoPoint>,
    val sections: List<Section>,
)

/** 내비에 올리는 코스 위의 한 지점. */
data class Stop(val name: String, val point: GeoPoint, val pathIndex: Int)

/** 내비에 한 번에 올리는 경로. 내비 제약으로 경유지는 최대 2개다. */
data class Leg(val waypoints: List<Stop>, val destination: Stop)
