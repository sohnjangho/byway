package com.byway.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111_195.0, GeoPoint(37.0, 127.0).distanceTo(GeoPoint(38.0, 127.0)), 50.0)
    }

    @Test
    fun longitudeShrinksWithLatitude() {
        assertEquals(88_000.0, GeoPoint(37.5, 127.0).distanceTo(GeoPoint(37.5, 128.0)), 500.0)
    }

    @Test
    fun distanceToSegmentMeasuresPerpendicularDistance() {
        val a = GeoPoint(37.0, 127.0)
        val b = GeoPoint(37.0, 127.1)
        val above = GeoPoint(37.01, 127.05)
        assertEquals(1_112.0, above.distanceToSegment(a, b), 5.0)
    }

    @Test
    fun distanceToSegmentClampsToTheNearestEnd() {
        val a = GeoPoint(37.0, 127.0)
        val b = GeoPoint(37.0, 127.1)
        val past = GeoPoint(37.0, 127.2)
        assertEquals(past.distanceTo(b), past.distanceToSegment(a, b), 5.0)
    }
}
