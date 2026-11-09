package com.byway.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTimingTest {
    @Test
    fun speechRightAfterARouteWaitsForTheNavigationToFinish() {
        assertEquals(2_500L, speechDelayMs(0L))
        assertEquals(1_500L, speechDelayMs(1_000L))
    }

    @Test
    fun speechLongAfterARouteIsNotDelayed() {
        assertEquals(0L, speechDelayMs(2_500L))
        assertEquals(0L, speechDelayMs(60_000L))
        assertEquals(0L, speechDelayMs(null))
    }

    /** 시계가 뒤로 가도 기다리는 시간은 한도를 넘지 않는다. */
    @Test
    fun theDelayNeverExceedsTheGap() {
        assertEquals(2_500L, speechDelayMs(-5_000L))
    }

    @Test
    fun aFixOlderThanTenSecondsIsStaleWhileDriving() {
        assertFalse(isLocationStale(9_999L, parked = false))
        assertTrue(isLocationStale(10_000L, parked = false))
    }

    @Test
    fun aParkedCarIsNeverStale() {
        assertFalse(isLocationStale(600_000L, parked = true))
    }
}
