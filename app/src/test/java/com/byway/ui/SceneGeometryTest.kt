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
        assertEquals(FadeBand(true, 490f, 550f, 1f), sceneFade(WindowClass.THIRD, list = true))
    }

    @Test
    fun fadeBands() {
        assertEquals(FadeBand(false, 1280f, 1680f, 0.2f), sceneFade(WindowClass.TWO))
        assertEquals(FadeBand(false, 960f, 1420f, 0.2f), sceneFade(WindowClass.FULL))
        assertEquals(FadeBand(true, 590f, 706f, 1f), sceneFade(WindowClass.THIRD))
    }

    @Test
    fun lateResultIsDropped() {
        val latest = LatestOnly()
        val first = latest.next()
        val second = latest.next()
        assertFalse(latest.isCurrent(first))
        assertTrue(latest.isCurrent(second))
    }
}
