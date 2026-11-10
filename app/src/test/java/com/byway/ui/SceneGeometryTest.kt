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
        assertEquals(FadeBand(false, 1448f, 1530f, 0.16f), sceneFade(WindowClass.TWO))
        assertEquals(FadeBand(false, 1088f, 1170f, 0.16f), sceneFade(WindowClass.FULL))
        assertEquals(FadeBand(true, 423f, 711f, 1f), sceneFade(WindowClass.THIRD))
    }

    /**
     * 지도는 글 칸 아래까지 옅게(16%) 깔리고, 글 칸 끝 300dp 앞에서부터 짙어져 칸 끝 바로 뒤에서 다 드러난다(화면 다듬기 설계 1절).
     * 경계 띠가 보이지 않고, 경로가 놓이는 자리 전에는 다 드러난다.
     */
    @Test
    fun theMapReachesUnderTheTextColumnAndIsClearBeforeTheRoute() {
        val routeLeft = 1540f
        listOf(WindowClass.TWO to (1700f to Dimens.SIDE_WIDTH), WindowClass.FULL to (2560f to Dimens.SIDE_WIDTH_FULL)).forEach { (window, size) ->
            val (viewW, side) = size
            val left = sceneRect(window, viewW, 1210f).left
            val band = sceneFade(window)
            val columnEnd = (Dimens.RAIL_WIDTH + side).toFloat()
            assertEquals(MAP_UNDER_TEXT, band.alphaFrom)
            assertEquals("$window 시작", columnEnd - Dimens.SPACE_8, left + band.from, 1f)
            assertTrue("$window 끝", left + band.to in columnEnd..columnEnd + 80f)
            assertTrue("$window 경로", band.to <= routeLeft)
        }
    }

    /**
     * 글자 아래에서는 지도가 16%를 넘지 않는다. 낮 위성 지도처럼 어두운 지도에서도 글자 대비 4.5:1을 지키기 위해서다(ThemeTest).
     * 글자의 오른쪽 끝은 글 칸 끝에서 안쪽 여백(SPACE_8)만큼 앞이다.
     */
    @Test
    fun theMapStaysFaintUnderTheText() {
        listOf(WindowClass.TWO to (1700f to Dimens.SIDE_WIDTH), WindowClass.FULL to (2560f to Dimens.SIDE_WIDTH_FULL)).forEach { (window, size) ->
            val (viewW, side) = size
            val left = sceneRect(window, viewW, 1210f).left
            val band = sceneFade(window)
            val textEnd = (Dimens.RAIL_WIDTH + side - Dimens.SPACE_8).toFloat()
            val x = textEnd - left
            val alpha = if (x <= band.from) band.alphaFrom else band.alphaFrom + (1 - band.alphaFrom) * (x - band.from) / (band.to - band.from)
            assertTrue("$window", alpha <= MAP_UNDER_TEXT + 0.001f)
            assertTrue("$window 가림", panelShade((Dimens.RAIL_WIDTH + side).toFloat()).solid >= textEnd)
        }
    }

    /** 한국 지도도 장면과 같은 띠로 글 칸 아래에 깔린다. */
    @Test
    fun theAtlasIsVeiledLikeTheScene() {
        val shade = panelShade(776f)
        assertEquals(744f, shade.solid)
        assertEquals(826f, shade.clear)
        assertEquals(1f - MAP_UNDER_TEXT, shade.veil)
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

    /** 1/3 홈은 줄인 장면의 왼쪽(경로가 오지 않는 자리)에 출처를 좁게 세운다. 경로와 번호를 가리지 않는다. */
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

    @Test
    fun theDriveBackdropMeetsTheMapWhereTheMapFadesInWideWindows() {
        val band = sceneFadeOnScreen(WindowClass.TWO, 1680f, 1210f)
        assertEquals(false, band.vertical)
        assertEquals(724f, band.from)
        assertEquals(806f, band.to)
    }

    /** 1/3 주행 화면은 사진 띠가 없어 줄이지 않은 장면을 쓴다. 배경은 그 장면이 옅어지는 띠에서 지도와 만난다. */
    @Test
    fun theDriveBackdropMeetsTheMapBelowItInTheThirdWindow() {
        val band = sceneFadeOnScreen(WindowClass.THIRD, 840f, 1210f)
        assertEquals(true, band.vertical)
        assertEquals(449f, band.from)
        assertEquals(737f, band.to)
    }
}
