package com.byway.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class SimplifyTest {
    private val straight = (0..10).map { GeoPoint(37.0, 127.0 + it * 0.01) }

    @Test
    fun straightRoadKeepsOnlyBothEnds() {
        assertEquals(listOf(0, 10), keyPointIndexes(straight, toleranceMeters = 100.0))
    }

    /** 올라갔다 내려오는 삼각형 길. 꼭짓점 하나만 남아야 한다. */
    private fun ridge(peakDegrees: Double) = (0..10).map {
        val height = peakDegrees * (5 - kotlin.math.abs(it - 5)) / 5
        GeoPoint(37.0 + height, 127.0 + it * 0.01)
    }

    @Test
    fun detourFromTheStraightLineIsKept() {
        assertEquals(listOf(0, 5, 10), keyPointIndexes(ridge(0.05), toleranceMeters = 100.0))
    }

    @Test
    fun smallWobbleBelowToleranceIsDropped() {
        assertEquals(listOf(0, 10), keyPointIndexes(ridge(0.0001), toleranceMeters = 100.0))
    }

    @Test
    fun tighterToleranceKeepsMorePoints() {
        assertEquals(listOf(0, 5, 10), keyPointIndexes(ridge(0.0001), toleranceMeters = 5.0))
    }
}
