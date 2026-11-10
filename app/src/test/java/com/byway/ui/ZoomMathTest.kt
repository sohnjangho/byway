package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    /** 축척 막대는 지도 폭의 4분의 1 안에서 가장 긴 깔끔한 길이다. */
    @Test
    fun scaleBarFitsAQuarter() {
        assertEquals(200, scaleBarMeters(1_500.0))
        assertEquals(500, scaleBarMeters(2_800.0))
        assertEquals(1_000, scaleBarMeters(6_000.0))
        assertEquals(5_000, scaleBarMeters(30_000.0))
        assertEquals(100, scaleBarMeters(200.0))
    }

    @Test
    fun scaleLabelUsesKmFromOneKilometer() {
        assertEquals("500 m", scaleLabel(500))
        assertEquals("1 km", scaleLabel(1_000))
        assertEquals("2 km", scaleLabel(2_000))
    }
}
