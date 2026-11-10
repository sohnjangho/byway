package com.byway.ui

import com.byway.core.course.Course

private const val SATELLITE = "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"

private const val TERRAIN = "Terrain Light · Data © OpenStreetMap contributors and others · Rendering © EOX"

/**
 * 지도 위에 적는 한 줄 출처(화면 다듬기 설계 5절). 권리자(EOX, OpenStreetMap)를 모두 남기고 전문은 앱 정보([creditLines])에 둔다.
 * 훑어보기 사진 띠는 위성이라 [withFrames]면 일반 지도 화면에도 위성 권리자를 붙인다.
 */
fun mapCreditLine(style: MapStyle, withFrames: Boolean = false): String = when (style) {
    MapStyle.SATELLITE -> "Sentinel-2 cloudless © EOX · 코스 © OpenStreetMap contributors"
    MapStyle.MAP -> if (withFrames) "Terrain Light, Sentinel-2 cloudless © EOX · © OpenStreetMap contributors" else "Terrain Light © EOX · © OpenStreetMap contributors"
}

/**
 * 지도 칩의 글. 보이는 지도의 출처([map])는 늘 적는다. 풍경 사진으로 넘겨도 글 칸 아래에 지도가 보이기 때문이다.
 * 흐린 배경 사진이 깔려 있으면 그 출처([backdrop], 변형 포함)를, 풍경 사진만 보이면 그 출처([photo])를 둘째 줄에 적는다.
 */
fun creditText(map: String, backdrop: String?, photo: String?, onPhoto: Boolean): String =
    listOfNotNull(map, (if (onPhoto) backdrop ?: photo else backdrop)?.takeIf { it.isNotBlank() }).joinToString("\n")

private val PHOTO_CREDIT = Regex("""^(?:사진\s*:?\s*)?(.+?)\s*\(([^,)]+)(?:,[^)]*)?\)\s*$""")

/**
 * 사진 위에 적는 짧은 출처. "사진: 작가 (라이선스, 출처)"를 "사진 작가 · 라이선스"로 줄인다.
 * CC BY 계열 사진을 흐리게 바꿔 쓰면([changed]) 변형을 밝힌다. CC0 와 퍼블릭 도메인은 밝힐 의무가 없다. 전문과 주소는 앱 정보에 있다.
 */
fun photoCreditLine(credit: String, changed: Boolean = false): String {
    if (credit.isBlank()) return ""
    val match = PHOTO_CREDIT.find(credit.trim()) ?: return "사진 " + credit.trim().removePrefix("사진:").removePrefix("사진").trim()
    val (author, licence) = match.destructured
    val altered = changed && licence.trim().startsWith("CC BY")
    return "사진 ${author.trim()} · ${licence.trim()}" + if (altered) " · 변형" else ""
}

private val FIXED_CREDITS = listOf(
    "© OpenStreetMap contributors (ODbL)",
    SATELLITE,
    "$TERRAIN (EOX::Maps, https://maps.eox.at)",
    "고도: Open-Meteo, Copernicus DEM",
)

/** 앱에 실린 코스, 사진, 지도 자료의 출처. */
fun creditLines(courses: List<Course>): List<String> =
    (courses.map { it.attribution } + courses.map { it.sceneryDetail.ifBlank { it.sceneryCredit } } + FIXED_CREDITS).filter { it.isNotBlank() }.distinct()

/** 앱에 함께 배포되는 오픈소스. docs/권리점검.md 와 같은 목록이다. */
val OPEN_SOURCE_NOTICES = listOf(
    "AndroidX Core, AppCompat — Apache License 2.0",
    "Kotlin 표준 라이브러리, kotlinx.serialization — Apache License 2.0",
    "OkHttp — Apache License 2.0",
)
