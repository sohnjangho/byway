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

    private val satellite = "Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)"

    @Test
    fun theSatelliteCreditNamesSatelliteAndRoads() {
        val credit = mapCredit(MapStyle.SATELLITE)
        assertTrue(credit.contains(satellite))
        assertTrue(credit.contains("OpenStreetMap"))
    }

    @Test
    fun theMapCreditNamesTheMapAndItsData() {
        val credit = mapCredit(MapStyle.MAP)
        assertTrue(credit.contains("Terrain Light"))
        assertTrue(credit.contains("OpenStreetMap contributors"))
        assertTrue(credit.contains("EOX"))
        assertTrue(!credit.contains("Sentinel"))
    }

    /** 훑어보기 사진 띠는 위성이라 일반 지도 화면에서도 위성 출처를 적는다. */
    @Test
    fun aMapScreenWithFramesAlsoNamesTheSatellite() {
        val credit = mapCredit(MapStyle.MAP, withFrames = true)
        assertTrue(credit.contains("Terrain Light"))
        assertTrue(credit.contains(satellite))
    }

    /** 1/3 창은 지도 칸이 좁아 짧게 적는다. 줄여도 권리자는 모두 남기고 전문이 있는 곳을 알린다. */
    @Test
    fun theShortCreditStillNamesEveryRightsHolder() {
        listOf(MapStyle.MAP, MapStyle.SATELLITE).forEach { style ->
            val short = shortMapCredit(style)
            assertTrue(short, short.contains("© OpenStreetMap contributors"))
            assertTrue(short, short.contains("EOX"))
            assertTrue(short, short.contains("Sentinel-2 cloudless"))
            assertTrue(short, short.contains("앱 정보"))
        }
        assertTrue(shortMapCredit(MapStyle.MAP).contains("Terrain Light"))
    }

    @Test
    fun theShortCreditIsAboutHalfTheFullOne() {
        assertTrue(shortMapCredit(MapStyle.MAP).length * 3 <= mapCredit(MapStyle.MAP, withFrames = true).length * 2)
        assertTrue(shortMapCredit(MapStyle.SATELLITE).length <= mapCredit(MapStyle.SATELLITE).length)
    }

    /** 짧게 적은 화면의 전문은 앱 정보에 있어야 한다. */
    @Test
    fun theFullTextOfTheShortCreditIsInAppInfo() {
        assertTrue(lines.any { it.contains("Contains modified Copernicus Sentinel data 2016") })
        assertTrue(lines.any { it.contains("Data © OpenStreetMap contributors and others · Rendering © EOX") })
    }

    @Test
    fun theMapSourceIsListedWithALinkBack() {
        assertTrue(lines.any { it.contains("Terrain Light") && it.contains("maps.eox.at") })
    }

    @Test
    fun theDriveMapCreditKeepsEveryRightsHolderInFewerWords() {
        val map = driveMapCredit(MapStyle.MAP)
        assertTrue(map.contains("EOX") && map.contains("OpenStreetMap"))
        val satellite = driveMapCredit(MapStyle.SATELLITE)
        assertTrue(satellite.contains("Sentinel-2") && satellite.contains("EOX") && satellite.contains("OpenStreetMap"))
    }

    @Test
    fun aBackdropPhotoAddsItsCreditAndSaysItWasChanged() {
        val text = driveCredit("지도", "사진: 누구 (CC BY-SA 3.0, Wikimedia Commons)")
        assertEquals("지도 · 배경 사진: 누구 (CC BY-SA 3.0, Wikimedia Commons), 흐리게 변형", text)
    }

    @Test
    fun withoutABackdropPhotoOnlyTheMapIsCredited() {
        assertEquals("지도", driveCredit("지도", null))
    }
}
