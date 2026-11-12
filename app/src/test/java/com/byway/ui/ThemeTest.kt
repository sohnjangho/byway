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

    private fun channel(color: Int, shift: Int): Double {
        val c = (color shr shift and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Int) = 0.2126 * channel(color, 16) + 0.7152 * channel(color, 8) + 0.0722 * channel(color, 0)

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun mix(under: Int, over: Int, opacity: Float): Int {
        fun part(shift: Int) = ((under shr shift and 0xFF) * (1 - opacity) + (over shr shift and 0xFF) * opacity).toInt()
        return (0xFF shl 24) or (part(16) shl 16) or (part(8) shl 8) or part(0)
    }

    private fun assertReadableOverPhoto(palette: Palette, worstPixel: Int, opacity: Float) {
        listOf(palette.airForest, palette.airCity, palette.airRiver).forEach { air ->
            val worst = mix(air, worstPixel, opacity)
            listOf(palette.ink, palette.inkSecondary).forEach { assertTrue(contrast(it, worst) >= 4.5) }
            listOf(palette.sectionForest, palette.sectionCity, palette.sectionRiver).forEach { assertTrue(contrast(it, worst) >= 3.0) }
        }
    }

    @Test
    fun nightTextStaysReadableOverAWhiteBackdropPhoto() {
        assertReadableOverPhoto(NightPalette, 0xFFFFFFFF.toInt(), Dimens.DRIVE_PHOTO_NIGHT)
    }

    @Test
    fun dayTextStaysReadableOverABlackBackdropPhoto() {
        assertReadableOverPhoto(DayPalette, 0xFF000000.toInt(), Dimens.DRIVE_PHOTO_DAY)
    }
}
