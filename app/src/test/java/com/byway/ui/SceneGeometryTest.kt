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

    /** 1/3 창의 홈은 지도 아래쪽에 훑어보기 사진 띠를 얹는다. 경로와 번호가 띠 뒤로 들어가지 않게 장면을 줄인다. */
    @Test
    fun theThirdWindowKeepsTheRouteAboveTheScrubStrip() {
        val routeBoxBottom = 1000f
        val pin = 24f
        val rect = sceneRect(WindowClass.THIRD, 841f, 1210f, strip = true)
        val stripTop = (Dimens.THIRD_SCENE_HEIGHT - ScrubStrip.HEIGHT - Dimens.SPACE_3).toFloat()
        assertTrue(rect.top + routeBoxBottom * rect.height / 1210f + pin <= stripTop)
        assertEquals(841f, rect.left + rect.width)
        assertEquals(2404f / 1210f, rect.width / rect.height, 0.01f)
    }

    /** 줄인 장면의 왼쪽은 경로가 오지 않는 자리다. 출처를 거기에 두어 경로와 번호를 가리지 않는다. */
    @Test
    fun theCreditSitsBesideTheRouteInTheShrunkThirdWindow() {
        val routeBoxLeft = 1540f
        val pin = 24f
        val rect = sceneRect(WindowClass.THIRD, 841f, 1210f, strip = true)
        val routeLeft = rect.left + routeBoxLeft * rect.width / 2404f
        assertTrue(Dimens.SPACE_3 + Dimens.THIRD_CREDIT_WIDTH + pin <= routeLeft)
    }

    /** 사진 띠가 없는 코스는 장면을 줄이지 않는다. */
    @Test
    fun withoutAScrubStripTheThirdWindowIsNotShrunk() {
        assertEquals(sceneRect(WindowClass.THIRD, 841f, 1210f), sceneRect(WindowClass.THIRD, 841f, 1210f, strip = false))
        assertEquals(sceneRect(WindowClass.TWO, 1700f, 1184f), sceneRect(WindowClass.TWO, 1700f, 1184f, strip = true))
    }

    /** 줄인 장면에서도 지도는 길게 옅어지고, 사진 띠에 닿기 전에 다 사라진다. */
    @Test
    fun theShrunkMapIsGoneBeforeTheScrubStrip() {
        val rect = sceneRect(WindowClass.THIRD, 841f, 1210f, strip = true)
        val band = sceneFade(WindowClass.THIRD, strip = true)
        val filmTop = (Dimens.THIRD_SCENE_HEIGHT - ScrubStrip.FILM_HEIGHT - Dimens.SPACE_3).toFloat()
        assertTrue(rect.top + band.to <= filmTop)
        assertTrue(band.to - band.from >= rect.height / 3)
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
