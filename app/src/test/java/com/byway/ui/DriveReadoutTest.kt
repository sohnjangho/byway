package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveReadoutTest {
    /** 구간 둘이 각각 10km 다. */
    private val meters = listOf(10_000.0, 10_000.0)

    @Test
    fun beforeTheFirstFixTheWholeCourseIsLeft() {
        val readout = readoutOf(meters, 0, null)
        assertEquals("20.0", readout.remainingKm)
        assertEquals(listOf(0f, 0f), readout.filled)
    }

    @Test
    fun theRemainingDistanceIsShownInKilometres() {
        assertEquals("7.5", readoutOf(meters, 1, 7_500.0).remainingKm)
    }

    @Test
    fun aPassedSectionIsFullAndTheCurrentOneIsPartlyFilled() {
        assertEquals(listOf(1f, 0.25f), readoutOf(meters, 1, 7_500.0).filled)
    }

    @Test
    fun theFirstSectionFillsWhileTheNextStaysEmpty() {
        assertEquals(listOf(0.5f, 0f), readoutOf(meters, 0, 15_000.0).filled)
    }
}
