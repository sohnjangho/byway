package com.byway.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class SimplifyTest {
    private val straight = (0..10).map { GeoPoint(37.0, 127.0 + it * 0.01) }

    /** 내비가 지름길을 고르는 구간에 지점을 하나 더할 때 쓴다. 두 끝을 이은 직선에서 가장 멀리 벗어난 점이다. */
    @Test
    fun theFarthestPointFromTheChordIsThePeak() {
        val (index, away) = requireNotNull(farthestFromChord(ridge(0.05), 0, 10))
        assertEquals(5, index)
        assertEquals(0.05 * 111_195, away, 100.0)
    }

    @Test
    fun aStretchWithNothingBetweenHasNoFarthestPoint() {
        assertEquals(null, farthestFromChord(straight, 3, 4))
        assertEquals(null, farthestFromChord(straight, 4, 4))
    }

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
