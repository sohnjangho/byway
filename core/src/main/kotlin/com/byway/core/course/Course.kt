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
    /** 추천 계절과 나이대. 앞의 값이 대표다. 주행 통계가 아니라 사람이 고른 편집 추천이다. */
    val seasons: List<String> = emptyList(),
    val ages: List<String> = emptyList(),
    /** 어울리는 날씨. 지금은 "rain" 하나다. */
    val weathers: List<String> = emptyList(),
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
    /** 풍경 사진의 출처. 비어 있으면 사진을 쓰지 않는다. */
    val sceneryCredit: String = "",
    /** 앱 정보에 적는 풍경 사진의 출처 전문. 제목과 라이선스, 원본 주소, 변형 사실까지 담는다. */
    val sceneryDetail: String = "",
    /** 지도(경로선) 없이 코스 정보만 있는 시연용 더미 코스. path 는 지점을 직선으로 이은 것이다. */
    val dummy: Boolean = false,
    /** 더미 코스의 표기 거리(m). 실제 도로로 달릴 때의 대략 거리다. */
    val declaredMeters: Double? = null,
)

/** 내비에 올리는 코스 위의 한 지점. */
data class Stop(val name: String, val point: GeoPoint, val pathIndex: Int)

/** 내비에 한 번에 올리는 경로. 내비 제약으로 경유지는 최대 2개다. */
data class Leg(val waypoints: List<Stop>, val destination: Stop)
