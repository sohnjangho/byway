package com.byway.ui

import com.byway.core.course.Course

private const val SATELLITE = "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"

/** 위성 그림과 코스 선이 보이는 화면의 출처. */
const val MAP_CREDIT = "$SATELLITE · 코스 © OpenStreetMap contributors"

private val FIXED_CREDITS = listOf(
    "© OpenStreetMap contributors (ODbL)",
    SATELLITE,
    "고도: Open-Meteo, Copernicus DEM",
)

/** 앱에 실린 코스, 사진, 지도 자료의 출처. */
fun creditLines(courses: List<Course>): List<String> =
    (courses.map { it.attribution } + courses.map { it.sceneryCredit } + FIXED_CREDITS).filter { it.isNotBlank() }.distinct()

/** 앱에 함께 배포되는 오픈소스. docs/권리점검.md 와 같은 목록이다. */
val OPEN_SOURCE_NOTICES = listOf(
    "AndroidX Core, AppCompat — Apache License 2.0",
    "Kotlin 표준 라이브러리, kotlinx.serialization — Apache License 2.0",
    "OkHttp — Apache License 2.0",
)
