package com.byway.core.course

import com.byway.core.geo.GeoPoint

/** 코스의 이름 붙은 구간. 인덱스는 [Course.path] 기준이고 양 끝을 포함한다. */
data class Section(
    val name: String,
    val startIndex: Int,
    val endIndex: Int,
    val mood: String? = null,
)

/** 코스 위의 볼거리. kind 는 view, food, rest, photo 중 하나다. */
data class Spot(val name: String, val kind: String, val point: GeoPoint, val pathIndex: Int)

/** 추천에 쓰는 분류. 값은 소문자 영문이고 화면 문구는 앱이 정한다. */
data class CourseTags(
    val themes: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val times: List<String> = emptyList(),
)

data class Course(
    val id: String,
    val name: String,
    val path: List<GeoPoint>,
    val sections: List<Section>,
    val spots: List<Spot> = emptyList(),
    val tags: CourseTags = CourseTags(),
    val summary: String = "",
    val attribution: String = "",
    /** 코스별 경유지 허용 오차. 없으면 기본값을 쓴다. */
    val toleranceMeters: Double? = null,
    /** 지역 묶음 이름. 없으면 지역 목록에서 빠진다. */
    val region: String = "",
    /** 코스를 따라 고르게 뽑은 고도(m). */
    val elevation: List<Double> = emptyList(),
)

/** 내비에 올리는 코스 위의 한 지점. */
data class Stop(val name: String, val point: GeoPoint, val pathIndex: Int)

/** 내비에 한 번에 올리는 경로. 내비 제약으로 경유지는 최대 2개다. */
data class Leg(val waypoints: List<Stop>, val destination: Stop)
