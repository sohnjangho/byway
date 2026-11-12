package com.byway.ui

import com.byway.core.geo.GeoFrame
import com.byway.core.geo.GeoPoint
import com.byway.core.geo.MapProjection
import com.byway.core.geo.Viewport
import com.byway.core.geo.tileZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** 화면에 보이는 조각 수의 최댓값. 확대 범위 폭 1.5~16km를 0.1km 간격으로 본다. */
    private fun mostTiles(viewW: Float, fitLeft: Float): Int = (15..160).maxOf { tenths ->
        val fitW = viewW - fitLeft
        val frame = GeoFrame(GeoPoint(37.5, 127.3), tenths * 100.0, tenths * 100.0 * 1210 / fitW)
        val map = MapProjection(frame, Viewport(fitLeft, 0f, fitW, 1210f))
        map.tilesFor(viewW, 1210f, tileZoom(map.pxPerWorld)).size
    }

    /** 메모리가 보이는 조각보다 적으면 그릴 때마다 지우고 다시 받는 고리가 돈다(최종 검토 C1). */
    @Test
    fun theMemoryHoldsTwoScreensOfTilesBesideTheTextAndOneFullScreen() {
        assertTrue("2/3 ${mostTiles(1682f, 776f)}", mostTiles(1682f, 776f) * 2 <= MAP_TILE_MEMORY)
        assertTrue("full ${mostTiles(2560f, 1276f)}", mostTiles(2560f, 1276f) <= MAP_TILE_MEMORY)
    }

    /** 범위가 바뀌면 새 조각부터 받는다(최종 검토 I3). */
    @Test
    fun theNewestTileRequestRunsFirst() {
        val queue = TileQueue()
        val first = Runnable {}
        val second = Runnable {}
        queue.offer(first)
        queue.offer(second)
        assertTrue(queue.take() === second)
        assertTrue(queue.take() === first)
    }

    /** 화면이 5초 동안 찾지 않은 조각은 받지 않는다. 범위가 바뀌었거나 화면을 떠난 것이다. */
    @Test
    fun aTileNobodyAskedForLatelyIsSkipped() {
        assertTrue(tileStillWanted(askedAt = 10_000L, now = 15_000L))
        assertFalse(tileStillWanted(askedAt = 10_000L, now = 15_001L))
        assertFalse(tileStillWanted(askedAt = null, now = 1L))
    }

    /** 오류 쪽지나 HTML 이 그림 자리에 저장되면 그 조각은 영영 비므로 그림 응답만 저장한다. */
    @Test
    fun onlyAnImageResponseIsKept() {
        assertTrue(isTileResponse(200, "image/jpeg"))
        assertFalse(isTileResponse(200, "text/html; charset=utf-8"))
        assertFalse(isTileResponse(404, "image/jpeg"))
        assertFalse(isTileResponse(200, null))
    }
}
