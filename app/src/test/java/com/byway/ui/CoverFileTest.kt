package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverFileTest {
    private val both = setOf("a.jpg", "a-night.jpg")
    private val dayOnly = setOf("a.jpg")
    private val every = both + setOf("a-map.jpg", "a-map-night.jpg")

    @Test
    fun dayUsesTheDayCover() {
        assertEquals("a.jpg", coverFile("a", night = false, both))
    }

    @Test
    fun nightUsesTheNightCover() {
        assertEquals("a-night.jpg", coverFile("a", night = true, both))
    }

    @Test
    fun nightFallsBackToTheDayCover() {
        assertEquals("a.jpg", coverFile("a", night = true, dayOnly))
    }

    /** 야간 파일만 있는 경우는 없다. 있어도 주간에 어두운 사진을 쓰지 않는다. */
    @Test
    fun dayDoesNotBorrowTheNightCover() {
        assertNull(coverFile("a", night = false, setOf("a-night.jpg")))
    }

    @Test
    fun aCourseWithoutCoverHasNoFile() {
        assertNull(coverFile("b", night = true, both))
    }

    @Test
    fun theMapStyleUsesItsOwnFiles() {
        assertEquals("a-map.jpg", coverFile("a", night = false, every, MapStyle.MAP))
        assertEquals("a-map-night.jpg", coverFile("a", night = true, every, MapStyle.MAP))
    }

    @Test
    fun theSatelliteStyleIgnoresMapFiles() {
        assertEquals("a-night.jpg", coverFile("a", night = true, every, MapStyle.SATELLITE))
    }

    /** 새 코스에 일반 지도 그림이 아직 없으면 위성 그림으로 대신한다. */
    @Test
    fun aCourseWithoutMapFilesFallsBackToTheSatellite() {
        assertEquals("a-night.jpg", coverFile("a", night = true, both, MapStyle.MAP))
        assertEquals("a.jpg", coverFile("a", night = false, both, MapStyle.MAP))
    }

    @Test
    fun aMissingMapNightFileUsesTheMapDayFile() {
        assertEquals("a-map.jpg", coverFile("a", night = true, both + "a-map.jpg", MapStyle.MAP))
    }

    @Test
    fun anUnknownSettingMeansTheMap() {
        assertEquals(MapStyle.MAP, MapStyle.of(null))
        assertEquals(MapStyle.MAP, MapStyle.of("something"))
        assertEquals(MapStyle.SATELLITE, MapStyle.of("satellite"))
    }

    /** 흰 번호 표시는 밝은 일반 지도에서 묻힌다. 그때만 어둡게 그린다. */
    @Test
    fun pinsAreDarkOnlyOnTheDayMap() {
        assertEquals(true, darkPinsOn(MapStyle.MAP, night = false))
        assertEquals(false, darkPinsOn(MapStyle.MAP, night = true))
        assertEquals(false, darkPinsOn(MapStyle.SATELLITE, night = false))
    }
}
