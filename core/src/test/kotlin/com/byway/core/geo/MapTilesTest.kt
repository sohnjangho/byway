package com.byway.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.pow

class MapTilesTest {
    private val fit = Viewport(800f, 0f, 880f, 1210f)
    private val center = GeoPoint(37.5, 127.3)
    private val frame = GeoFrame(center, 3_000.0, 3_000.0 * 1210 / 880)

    /** 조각 왼쪽 위 모서리의 위경도. 조각 좌표의 정의(웹 메르카토르)에서 바로 구한다. */
    private fun cornerOf(z: Int, x: Int, y: Int): GeoPoint {
        val n = 2.0.pow(z)
        val lon = x / n * 360 - 180
        val lat = Math.toDegrees(2 * atan(exp(PI * (1 - 2 * y / n))) - PI / 2)
        return GeoPoint(lat, lon)
    }

    @Test
    fun theFrameCenterLandsAtTheCenterOfTheFitArea() {
        val map = MapProjection(frame, fit)
        assertEquals(1240f, map.x(center), 0.01f)
        assertEquals(605f, map.y(center), 0.01f)
    }

    @Test
    fun theFrameFillsTheFitArea() {
        val map = MapProjection(frame, fit)
        assertEquals(1680f, map.x(GeoPoint(center.lat, frame.east)), 2f)
        assertEquals(800f, map.x(GeoPoint(center.lat, frame.west)), 2f)
        assertEquals(0f, map.y(GeoPoint(frame.north, center.lon)), 2f)
        assertEquals(1210f, map.y(GeoPoint(frame.south, center.lon)), 2f)
    }

    @Test
    fun aWideFrameIsLimitedByTheWidth() {
        val wide = GeoFrame(center, 6_000.0, 1_000.0)
        val map = MapProjection(wide, fit)
        assertEquals(1680f, map.x(GeoPoint(center.lat, wide.east)), 2f)
        assertTrue(map.y(GeoPoint(wide.north, center.lon)) > 100f)
    }

    @Test
    fun tilesLineUpWithTheProjectionAtSouthAndNorthCourses() {
        for (lat in listOf(33.4, 37.9)) {
            val here = GeoFrame(GeoPoint(lat, 126.5), 4_000.0, 5_000.0)
            val map = MapProjection(here, fit)
            val z = tileZoom(map.pxPerWorld)
            map.tilesFor(1680f, 1210f, z).forEach { spot ->
                val corner = cornerOf(spot.z, spot.x, spot.y)
                assertEquals(map.x(corner), spot.left, 0.5f)
                assertEquals(map.y(corner), spot.top, 0.5f)
            }
        }
    }

    @Test
    fun aTileIsDrawnAtTheSizeOfItsShareOfTheWorld() {
        val map = MapProjection(frame, fit)
        val z = tileZoom(map.pxPerWorld)
        val spot = map.tilesFor(1680f, 1210f, z).first()
        assertEquals((map.pxPerWorld / 2.0.pow(z)).toFloat(), spot.size, 0.01f)
    }

    @Test
    fun theTilesCoverTheWholeView() {
        val map = MapProjection(frame, fit)
        val spots = map.tilesFor(1680f, 1210f, tileZoom(map.pxPerWorld))
        assertTrue(spots.minOf { it.left } <= 0f)
        assertTrue(spots.minOf { it.top } <= 0f)
        assertTrue(spots.maxOf { it.left + it.size } >= 1680f)
        assertTrue(spots.maxOf { it.top + it.size } >= 1210f)
    }

    @Test
    fun theZoomPutsAboutOneTilePixelOnOneScreenPixelWithinTenToFifteen() {
        assertEquals(13, tileZoom(256 * 2.0.pow(13.4)))
        assertEquals(14, tileZoom(256 * 2.0.pow(13.6)))
        assertEquals(15, tileZoom(256 * 2.0.pow(17.0)))
        assertEquals(10, tileZoom(256 * 2.0.pow(8.0)))
    }

    @Test
    fun aPointAlongThePathIsFoundByDistance() {
        val path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.0, 127.01), GeoPoint(37.01, 127.01))
        val first = path[0].distanceTo(path[1]) / 1000
        assertEquals(path[0], pointAt(path, 0.0))
        assertEquals(path[2], pointAt(path, 99.0))
        val half = pointAt(path, first / 2)!!
        assertEquals(37.0, half.lat, 1e-9)
        assertEquals(127.005, half.lon, 1e-6)
        assertNull(pointAt(emptyList(), 1.0))
    }
}
