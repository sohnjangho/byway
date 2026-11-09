package com.byway.ui

import com.byway.core.course.Course

private const val SATELLITE = "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"

private const val TERRAIN = "Terrain Light · Data © OpenStreetMap contributors and others · Rendering © EOX"

/** 지도와 코스 선이 보이는 화면의 출처. 훑어보기 사진 띠는 위성이라 [withFrames]면 위성 출처를 함께 적는다. */
fun mapCredit(style: MapStyle, withFrames: Boolean = false): String = when {
    style == MapStyle.SATELLITE -> "$SATELLITE · 코스 © OpenStreetMap contributors"
    withFrames -> "$TERRAIN · 사진 띠: $SATELLITE"
    else -> TERRAIN
}

/** 지도 칸이 좁은 화면의 출처. 권리자는 모두 남기고 전문은 앱 정보([creditLines])에 둔다. 사진 띠가 있는 1/3 창 홈이 쓴다. */
fun shortMapCredit(style: MapStyle): String = when (style) {
    MapStyle.SATELLITE -> "Sentinel-2 cloudless © EOX · 코스 © OpenStreetMap contributors · 전체 출처는 앱 정보"
    MapStyle.MAP -> "Terrain Light © EOX · © OpenStreetMap contributors · 사진 띠 Sentinel-2 cloudless · 전체 출처는 앱 정보"
}

/** 주행 화면 지도의 출처. 글 칸이 좁고 사진 띠가 없어 지도와 길의 권리자만 적는다. 전문은 앱 정보에 있다. */
fun driveMapCredit(style: MapStyle): String = when (style) {
    MapStyle.SATELLITE -> "Sentinel-2 cloudless © EOX · 코스 © OpenStreetMap contributors"
    MapStyle.MAP -> "Terrain Light © EOX · © OpenStreetMap contributors"
}

private val FIXED_CREDITS = listOf(
    "© OpenStreetMap contributors (ODbL)",
    SATELLITE,
    "$TERRAIN (EOX::Maps, https://maps.eox.at)",
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
