package com.byway.ui

import com.byway.core.course.AtlasSpot
import com.byway.core.course.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class AtlasCameraMathTest {
    @Test
    fun focusAreasMatchTheMockups() {
        assertEquals(Box(196f, 40f, 744f, 1104f), atlasFocus(Tab.BROWSE, WindowClass.TWO))
        assertEquals(Box(196f, 40f, 1324f, 1130f), atlasFocus(Tab.BROWSE, WindowClass.FULL))
        assertEquals(Box(24f, 116f, 793f, 440f), atlasFocus(Tab.BROWSE, WindowClass.THIRD))
        assertEquals(Box(816f, 96f, 844f, 960f), atlasFocus(Tab.MINE, WindowClass.TWO))
        assertEquals(Box(1316f, 96f, 1204f, 990f), atlasFocus(Tab.MINE, WindowClass.FULL))
        assertEquals(Box(24f, 116f, 793f, 480f), atlasFocus(Tab.MINE, WindowClass.THIRD))
    }

    @Test
    fun growAddsRoomAroundTheBox() {
        assertEquals(Box(-20f, -20f, 140f, 140f), grow(Box(0f, 0f, 100f, 100f), 0.2f, 10f))
    }

    @Test
    fun growKeepsAMinimumSizeAroundTheCentre() {
        val grown = grow(Box(100f, 100f, 10f, 10f), 0f, 120f)
        assertEquals(Box(45f, 45f, 120f, 120f), grown)
    }

    @Test
    fun unionCoversEveryBox() {
        assertEquals(Box(0f, 0f, 30f, 40f), union(listOf(Box(0f, 0f, 10f, 10f), Box(20f, 30f, 10f, 10f))))
    }

    @Test
    fun fitUsesTheTighterSideAndCentresTheBox() {
        val camera = fit(Box(100f, 200f, 100f, 400f), Box(0f, 0f, 800f, 800f))
        assertEquals(AtlasCamera(150f, 400f, 2f), camera)
    }

    @Test
    fun cameraMapsTheCentreToTheFocusCentre() {
        val focus = Box(100f, 100f, 400f, 200f)
        val screen = toScreen(50f, 60f, AtlasCamera(50f, 60f, 3f), focus)
        assertEquals(300f, screen.x, 0.01f)
        assertEquals(200f, screen.y, 0.01f)
    }

    @Test
    fun courseCameraLeavesRoomAroundTheCourse() {
        val box = Box(100f, 100f, 200f, 200f)
        val spot = AtlasSpot(0f, 0f, box, box, emptyList())
        val focus = Box(0f, 0f, 800f, 800f)
        assertEquals(fit(grow(box, 0.18f, 120f), focus), courseCamera(spot, focus))
    }

    @Test
    fun scaleStaysWithinLimits() {
        assertEquals(0.3f, clampScale(0.01f, 0.3f, 24f), 0f)
        assertEquals(24f, clampScale(99f, 0.3f, 24f), 0f)
        assertEquals(2f, clampScale(2f, 0.3f, 24f), 0f)
    }
}
