package com.byway.ui

import com.byway.core.course.FrameInfo
import com.byway.core.course.Px
import com.byway.core.course.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubMathTest {
    private val track = listOf(TrackPoint(0f, 0f, 0.0), TrackPoint(100f, 0f, 10.0), TrackPoint(100f, 100f, 20.0))
    private val frames = listOf(0.0, 10.0, 20.0).map { FrameInfo(it, "구간", "forest", null, null) }

    @Test
    fun theStripPositionBecomesADistanceAlongTheCourse() {
        assertEquals(0.0, scrubKm(0, 1000, 50.0), 0.0)
        assertEquals(25.0, scrubKm(500, 1000, 50.0), 0.0)
    }

    @Test
    fun pullingPastEitherEndStaysOnTheCourse() {
        assertEquals(50.0, scrubKm(2000, 1000, 50.0), 0.0)
        assertEquals(0.0, scrubKm(-40, 1000, 50.0), 0.0)
        assertEquals(0.0, scrubKm(10, 0, 50.0), 0.0)
    }

    @Test
    fun theMarkerMovesBetweenTrackPoints() {
        assertEquals(Px(50f, 0f), trackAt(track, 5.0))
        assertEquals(Px(100f, 50f), trackAt(track, 15.0))
    }

    @Test
    fun theMarkerStopsAtTheEnds() {
        assertEquals(Px(0f, 0f), trackAt(track, -1.0))
        assertEquals(Px(100f, 100f), trackAt(track, 99.0))
    }

    @Test
    fun aCourseWithoutATrackHasNoMarker() {
        assertNull(trackAt(emptyList(), 5.0))
    }

    @Test
    fun theNearestFrameDescribesThePoint() {
        assertEquals(0, frameAt(frames, 4.0))
        assertEquals(1, frameAt(frames, 6.0))
        assertEquals(2, frameAt(frames, 100.0))
        assertEquals(-1, frameAt(emptyList(), 1.0))
    }

    @Test
    fun theCaptionNamesSectionHeightAndNearbyPlace() {
        assertEquals("17.1km 지점", scrubKmText(17.12))
        assertEquals("숲길 · 해발 451m · 수어장대 근처", scrubWhere(FrameInfo(17.1, "숲길", "forest", 451, 2), "수어장대"))
        assertEquals("강변", scrubWhere(FrameInfo(40.0, "강변", "river", null, null), null))
    }

    /** 사진의 가운데가 기준선 밑에 올 때 그 사진의 지점이 읽힌다. 사진 16장은 코스를 15칸으로 나눈다. */
    @Test
    fun eachPhotoIsReadWhenItsCentreIsUnderTheLine() {
        val step = 153
        val travel = filmTravel(count = 16, stepPx = step)
        assertEquals(15 * 153, travel)
        (0..15).forEach { i -> assertEquals(45.0 * i / 15, scrubKm(i * step, travel, 45.0), 1e-9) }
    }

    @Test
    fun aSinglePhotoDoesNotScroll() {
        assertEquals(0, filmTravel(count = 1, stepPx = 153))
        assertEquals(0, filmTravel(count = 0, stepPx = 153))
    }

    /** 주행 화면의 점은 남은 거리로 자리를 찾는다. */
    @Test
    fun theDrivenDistanceComesFromTheRemainingDistance() {
        assertEquals(20.0, drivenKm(50.0, 30_000.0), 1e-9)
    }

    @Test
    fun beforeTheFirstFixTheCarIsAtTheStart() {
        assertEquals(0.0, drivenKm(50.0, null), 1e-9)
    }

    /** 반올림 차이로 남은 거리가 전체보다 커도 코스 밖으로 나가지 않는다. */
    @Test
    fun theDrivenDistanceStaysOnTheCourse() {
        assertEquals(0.0, drivenKm(50.0, 50_400.0), 1e-9)
        assertEquals(50.0, drivenKm(50.0, -10.0), 1e-9)
    }
}
