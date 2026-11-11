package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TileLookTest {
    private val nightBg = rgbOf(19, 20, 23)

    private fun rgbOf(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun assertNear(expected: Triple<Int, Int, Int>, actual: Int) {
        val got = Triple(actual shr 16 and 0xFF, actual shr 8 and 0xFF, actual and 0xFF)
        listOf(expected.first to got.first, expected.second to got.second, expected.third to got.third).forEach { (want, have) ->
            assertEquals("$expected vs $got", want.toDouble(), have.toDouble(), 1.0)
        }
        assertEquals(0xFF, actual ushr 24)
    }

    @Test
    fun tileAddressesAreTheSameLayersAsTheSceneTool() {
        assertEquals("https://tiles.maps.eox.at/wmts/1.0.0/terrain-light_3857/default/g/14/6340/13976.jpg", tileUrl(MapStyle.MAP, 14, 13976, 6340))
        assertEquals("https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/14/6340/13976.jpg", tileUrl(MapStyle.SATELLITE, 14, 13976, 6340))
    }

    /** 기댓값은 tools/make-covers.py 의 night_map 을 1화소 그림에 돌린 값이다. */
    @Test
    fun theNightMapMatchesTheSceneTool() {
        assertNear(Triple(19, 20, 23), nightMapPixel(rgbOf(170, 190, 215), nightBg))
        assertNear(Triple(45, 46, 49), nightMapPixel(rgbOf(246, 246, 246), nightBg))
        assertNear(Triple(39, 40, 43), nightMapPixel(rgbOf(255, 255, 255), nightBg))
        assertNear(Triple(72, 73, 76), nightMapPixel(rgbOf(200, 215, 190), nightBg))
        assertNear(Triple(58, 59, 62), nightMapPixel(rgbOf(230, 228, 222), nightBg))
        assertNear(Triple(47, 48, 51), nightMapPixel(rgbOf(200, 205, 210), nightBg))
        assertNear(Triple(103, 104, 107), nightMapPixel(rgbOf(120, 130, 110), nightBg))
    }

    /** 도구의 darkened 와 같은 밝기·대비이고, 대비 기준값은 조각마다 다르지 않게 장면 평균 45로 고정한다. */
    @Test
    fun theNightSatelliteIsDarkenedAroundAFixedPivot() {
        assertNear(Triple(78, 85, 71), nightSatPixel(rgbOf(120, 130, 110)))
        assertNear(Triple(133, 127, 113), nightSatPixel(rgbOf(200, 190, 170)))
        assertNear(Triple(15, 22, 19), nightSatPixel(rgbOf(30, 40, 35)))
    }
}
