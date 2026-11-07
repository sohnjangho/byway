package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    /** 차량은 UI 종류 비트에 car(3)가 켜져 있다. 야간은 0x20, 주간은 0x10 이다. */
    private val carNight = 0x23
    private val carDay = 0x13
    private val carUndefined = 0x03

    @Test
    fun theCarInNightModeIsNight() {
        assertTrue(isNight(carNight))
    }

    @Test
    fun theCarInDayModeIsDay() {
        assertFalse(isNight(carDay))
    }

    @Test
    fun anUndecidedCarIsDay() {
        assertFalse(isNight(carUndefined))
    }

    @Test
    fun nightModeReadsTheNightColors() {
        assertEquals(NightPalette, paletteFor(carNight))
    }

    @Test
    fun dayModeReadsTheDayColors() {
        assertEquals(DayPalette, paletteFor(carDay))
    }

    @Test
    fun aMoodPicksItsSectionColorInTheCurrentPalette() {
        assertEquals(DayPalette.sectionForest, DayPalette.moodColor("forest"))
        assertEquals(NightPalette.sectionRiver, NightPalette.moodColor("river"))
        assertEquals(DayPalette.sectionCity, DayPalette.moodColor("city"))
    }

    @Test
    fun aSectionWithoutMoodReadsAsCity() {
        assertEquals(DayPalette.sectionCity, DayPalette.moodColor(null))
    }

    @Test
    fun aMoodPicksItsDriveBackgroundInTheCurrentPalette() {
        assertEquals(NightPalette.airForest, NightPalette.airColor("forest"))
        assertEquals(DayPalette.airRiver, DayPalette.airColor("river"))
        assertEquals(DayPalette.airCity, DayPalette.airColor(null))
    }
}
