package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.ScenePlace

/** 더미 코스의 지도 자리 문구(더 많은 코스 설계 6절). */
const val NO_MAP = "지도 없음 · 더미 데이터입니다"

/** 한국 지도에서 더미 코스를 골랐을 때 지도 위에 띄우는 문구. */
const val ATLAS_DUMMY_NOTE = "더미 데이터입니다 · 경로선이 없습니다"

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
