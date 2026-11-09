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

    @Test
    fun theMapSourceIsListedWithALinkBack() {
        assertTrue(lines.any { it.contains("Terrain Light") && it.contains("maps.eox.at") })
    }
}
