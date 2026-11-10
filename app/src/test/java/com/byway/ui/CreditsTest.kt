package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsTest {
    private val path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.1, 127.1))
    private fun course(id: String, attribution: String, photo: String) =
        Course(id, id, path, listOf(Section("구간", 0, 1)), attribution = attribution, sceneryCredit = photo)

    private val lines = creditLines(listOf(course("a", "코스 출처", "사진: 가"), course("b", "코스 출처", "")))

    @Test
    fun everyCourseCreditIsListedOnce() {
        assertEquals(1, lines.count { it == "코스 출처" })
        assertTrue(lines.contains("사진: 가"))
    }

    /** 앱 정보에는 칩의 짧은 출처 대신 제목과 주소까지 있는 전문을 적는다. */
    @Test
    fun thePhotoListShowsTheFullCreditInsteadOfTheShortOne() {
        val full = course("c", "코스 출처", "사진: 가").copy(sceneryDetail = "사진: 가, 원본 주소")
        val listed = creditLines(listOf(full))
        assertTrue(listed.contains("사진: 가, 원본 주소"))
        assertTrue(!listed.contains("사진: 가"))
    }

    @Test
    fun blankCreditsAreLeftOut() {
        assertTrue(lines.none { it.isBlank() })
    }

    @Test
    fun dataSourcesAreAlwaysListed() {
        assertTrue(lines.any { it.contains("OpenStreetMap") })
        assertTrue(lines.any { it.contains("Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)") })
        assertTrue(lines.any { it.contains("Open-Meteo") })
    }

    @Test
    fun openSourceNoticesNameTheLicence() {
        assertTrue(OPEN_SOURCE_NOTICES.isNotEmpty())
        OPEN_SOURCE_NOTICES.forEach { assertTrue(it, it.contains("Apache")) }
    }

    /** 지도 위 출처는 한 줄이다. 권리자(EOX, OpenStreetMap)는 모두 남기고 전문은 앱 정보에 둔다. */
    @Test
    fun mapCreditIsOneShortLine() {
        assertEquals("Terrain Light © EOX · © OpenStreetMap contributors", mapCreditLine(MapStyle.MAP))
        assertEquals("Sentinel-2 cloudless © EOX · 코스 © OpenStreetMap contributors", mapCreditLine(MapStyle.SATELLITE))
        MapStyle.entries.forEach { assertTrue(mapCreditLine(it).length <= 60) }
    }

    /** 훑어보기 사진 띠는 위성이라 일반 지도 화면에서도 위성 권리자를 적는다. */
    @Test
    fun aMapScreenWithFramesAlsoNamesTheSatellite() {
        val credit = mapCreditLine(MapStyle.MAP, withFrames = true)
        assertTrue(credit.contains("Terrain Light") && credit.contains("Sentinel-2 cloudless"))
        assertEquals(mapCreditLine(MapStyle.SATELLITE), mapCreditLine(MapStyle.SATELLITE, withFrames = true))
    }

    /** 사진 출처는 작가와 라이선스만 짧게 적는다. CC BY 계열을 흐리게 바꿔 쓰면 변형을 밝힌다. */
    @Test
    fun photoCreditKeepsAuthorAndLicence() {
        assertEquals("사진 Katsujiro Maekawa · CC0", photoCreditLine("사진: Katsujiro Maekawa (CC0, Wikimedia Commons)"))
        assertEquals("사진 Katsujiro Maekawa · CC0", photoCreditLine("사진: Katsujiro Maekawa (CC0, Wikimedia Commons)", changed = true))
        assertEquals("사진 Ronnie Dayo · CC BY-SA 4.0 · 변형", photoCreditLine("사진: Ronnie Dayo (CC BY-SA 4.0, Wikimedia Commons)", changed = true))
        assertEquals("사진 Ronnie Dayo · CC BY-SA 4.0", photoCreditLine("사진: Ronnie Dayo (CC BY-SA 4.0, Wikimedia Commons)"))
        assertEquals("사진 누구", photoCreditLine("누구"))
        assertEquals("", photoCreditLine(""))
    }

    /** 1/3 칸(841dp)에서도 한 줄 출처는 한 줄, 사진 띠 출처를 더해도 두 줄 안이다. 글자 폭은 크기의 0.55배로 어림한다. */
    @Test
    fun creditsFitTwoLinesInTheThirdWindow() {
        val room = 841 - 48
        val width = { text: String -> text.length * Dimens.CREDIT * 0.55f }
        MapStyle.entries.forEach { assertTrue(width(mapCreditLine(it)) <= room) }
        assertTrue(width(mapCreditLine(MapStyle.MAP, withFrames = true)) <= 2 * room)
        assertTrue(width("사진 H. Y. Shin 000 · CC0") <= room)
    }

    /** 짧게 적은 화면의 전문은 앱 정보에 있어야 한다. */
    @Test
    fun everyRightsHolderIsStillInAppInfo() {
        assertTrue(lines.any { it.contains("Contains modified Copernicus Sentinel data 2016") })
        assertTrue(lines.any { it.contains("Data © OpenStreetMap contributors and others · Rendering © EOX") })
    }

    @Test
    fun theMapSourceIsListedWithALinkBack() {
        assertTrue(lines.any { it.contains("Terrain Light") && it.contains("maps.eox.at") })
    }
}
