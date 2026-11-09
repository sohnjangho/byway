package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneGeometryTest {
    @Test
    fun twoWindowIsRightAlignedAndCentered() {
        assertEquals(SceneRect(1700f - 2404f, 1184f / 2 - 605f, 2404f, 1210f), sceneRect(WindowClass.TWO, 1700f, 1184f))
    }

    @Test
    fun fullWindowIsRightAligned() {
        assertEquals(SceneRect(2560f - 2404f, 0f, 2404f, 1210f), sceneRect(WindowClass.FULL, 2560f, 1210f))
    }

    @Test
    fun thirdWindowIsScaled() {
        assertEquals(SceneRect(841f - 1683f, 26f, 1683f, 847f), sceneRect(WindowClass.THIRD, 841f, 1210f))
    }

    @Test
    fun listScreenDrawsTheThirdWindowSmaller() {
        assertEquals(SceneRect(841f - 1394f, 70f, 1394f, 702f), sceneRect(WindowClass.THIRD, 841f, 1210f, list = true))
        assertEquals(FadeBand(true, 309f, 533f, 1f), sceneFade(WindowClass.THIRD, list = true))
    }

    @Test
    fun fadeBands() {
        assertEquals(FadeBand(false, 1460f, 1530f, 0f), sceneFade(WindowClass.TWO))
        assertEquals(FadeBand(false, 1140f, 1220f, 0f), sceneFade(WindowClass.FULL))
        assertEquals(FadeBand(true, 423f, 711f, 1f), sceneFade(WindowClass.THIRD))
    }

    /** 글 칸은 바탕색이고 지도는 그 오른쪽에서 짧게 시작한다. 경로가 놓이는 자리 전에 다 드러난다. */
    @Test
    fun theMapStartsBesideTheTextColumnAndIsClearBeforeTheRoute() {
        val routeLeft = 1540f
        listOf(WindowClass.TWO to (1700f to Dimens.SIDE_WIDTH), WindowClass.FULL to (2560f to Dimens.SIDE_WIDTH_FULL)).forEach { (window, size) ->
            val (viewW, side) = size
            val left = sceneRect(window, viewW, 1210f).left
            val band = sceneFade(window)
            val columnEnd = (Dimens.RAIL_WIDTH + side).toFloat()
            assertEquals(0f, band.alphaFrom)
            assertTrue("$window 시작", left + band.from in columnEnd - 40f..columnEnd + 40f)
            assertTrue("$window 끝", band.to <= routeLeft)
        }
    }

    /** 1/3 창은 지도가 길게 옅어져 아래 글 칸과 경계가 보이지 않는다. */
    @Test
    fun theThirdWindowFadesOverAThirdOfThePicture() {
        val band = sceneFade(WindowClass.THIRD)
        assertTrue(band.to - band.from >= 847f / 3)
    }

    @Test
    fun lateResultIsDropped() {
        val latest = LatestOnly()
        val first = latest.next()
        val second = latest.next()
        assertFalse(latest.isCurrent(first))
        assertTrue(latest.isCurrent(second))
    }

    @Test
    fun photoFadesUnderTheTextColumn() {
        assertEquals(FadeBand(false, 756f, 826f, 0f), photoFade(WindowClass.TWO, 1184f))
        assertEquals(FadeBand(false, 1256f, 1336f, 0f), photoFade(WindowClass.FULL, 1210f))
        assertEquals(FadeBand(true, 365f, 657f, 1f), photoFade(WindowClass.THIRD, 730f))
    }
}
