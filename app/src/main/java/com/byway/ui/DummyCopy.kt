package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.ScenePlace
import com.byway.core.geo.GeoPoint
import com.byway.core.geo.pointAt

/** 더미 코스의 선 옆 표시. 지도 조각 없이 예시 경로만 그린다(PM 11-11 "지도는 없더라도 코스경로는 더미로"). */
const val DUMMY_ROUTE = "더미 경로"

/** 한국 지도에서 더미 코스를 골랐을 때 지도 위에 띄우는 문구. */
const val ATLAS_DUMMY_NOTE = "더미 데이터입니다 · 경로는 예시입니다"

/** 더미 코스 이름 앞에 붙이는 낱말. 더미 이름이 보이는 곳에는 이 낱말도 보인다. */
const val DUMMY_WORD = "더미"

/** 경로선에서 잰 값(커브, 운전 즐거움 등). 더미 코스는 잴 선이 없어 "—"로 둔다. */
fun measured(course: Course, text: String): String = if (course.dummy) "—" else text

fun withDummyMark(course: Course, name: String): String = if (course.dummy) "$DUMMY_WORD · $name" else name

/** 더미 코스의 주행 화면 "남은 지점" 목록. 구간마다 그 끝 지점 하나, 거리는 처음부터 잰 누적 km 다. */
fun dummyPlaces(course: Course, sectionMeters: List<Double>): List<ScenePlace> {
    val ends = sectionMeters.runningReduce(Double::plus)
    return course.sections.mapIndexed { i, section ->
        val last = i == course.sections.lastIndex
        ScenePlace(i + 1, section.name, "stop", if (last) "도착" else "경유", "", ends[i] / 1_000, 0f, 0f)
    }
}

/**
 * 더미 코스 주행 화면의 위치 점. 실제 위치는 예시 경로 밖에 있을 수 있어, 달린 거리(표기 거리 km)만큼 예시 경로를 따라간 자리에 둔다.
 * [scale]은 표기 거리 ÷ 예시 경로 길이다.
 */
fun dummyDot(course: Course, drivenKm: Double, scale: Double): GeoPoint? = pointAt(course.path, drivenKm / scale)
