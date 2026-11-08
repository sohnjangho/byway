package com.byway.ui

import com.byway.core.course.MoodLook
import com.byway.core.course.Px
import com.byway.core.course.SceneMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodMotionTest {
    private val rain = MoodLook("streak", 46, "#b9d2ec", "#5c7f9f", "rgba(80, 110, 145, 0.26)", -140f, 640f, 0.8f, 1.3f)
    private val still = MoodLook("star", 60, "#ffffff", "#6a7fb0", "rgba(40, 55, 110, 0.36)", 0f, 0f, 0f, 0f)

    private fun assertNear(expected: Px, actual: Px) {
        assertEquals(expected.x, actual.x, 0.5f)
        assertEquals(expected.y, actual.y, 0.5f)
    }

    @Test
    fun moodsHaveKeysExceptPlain() {
        assertEquals("rain", moodKey(SceneMood.RAIN))
        assertEquals("sunset", moodKey(SceneMood.SUNSET))
        assertNull(moodKey(SceneMood.PLAIN))
    }

    @Test
    fun rainIsHalfWayThroughItsPeriod() {
        assertNear(Px(-70f, 320f), tileShift(rain, 0.4f, 0))
    }

    @Test
    fun farLayerUsesItsOwnPeriod() {
        assertNear(Px(-70f, 320f), tileShift(rain, 0.65f, 1))
    }

    @Test
    fun motionRestartsAfterEachPeriod() {
        assertNear(tileShift(rain, 0.2f, 0), tileShift(rain, 1.0f, 0))
    }

    @Test
    fun stillMoodDoesNotMove() {
        assertEquals(Px(0f, 0f), tileShift(still, 5f, 0))
    }

    @Test
    fun cssColorsAreRead() {
        assertEquals(0x42506E91, parseRgba("rgba(80, 110, 145, 0.26)"))
        assertEquals(0xFFB9D2EC.toInt(), parseRgba("#b9d2ec"))
    }
}
