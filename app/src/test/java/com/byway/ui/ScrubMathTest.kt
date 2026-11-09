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

    /** 훑어보기 자료가 없는 코스는 다른 코스 띠만 보인다. */
    @Test
    fun aCourseWithoutFramesShowsOnlyTheCourseStrip() {
        assertEquals(StripMode.SCRUB, stripMode(wantsScrub = true, hasFrames = true))
        assertEquals(StripMode.COURSES, stripMode(wantsScrub = true, hasFrames = false))
        assertEquals(StripMode.COURSES, stripMode(wantsScrub = false, hasFrames = true))
    }
}
