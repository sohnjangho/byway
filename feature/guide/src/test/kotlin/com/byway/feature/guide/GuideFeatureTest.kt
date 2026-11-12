package com.byway.feature.guide

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.course.Spot
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideFeatureTest {
    private val spotPoint = GeoPoint(37.0, 127.1)
    private val course = Course(
        "c", "코스", listOf(GeoPoint(37.0, 127.0), spotPoint), listOf(Section("앞", 0, 1)),
        spots = listOf(Spot("전망대", "view", spotPoint, 1), Spot("쉼터", "rest", GeoPoint(37.0, 127.2), 1)),
    )
    private val near = GeoPoint(37.0, 127.095)
    private val far = GeoPoint(37.0, 127.0)

    private fun started() = GuideFeature().also { it.onEvent(DriveEvent.CourseStarted(course)) }

    @Test
    fun aSpotIsAnnouncedWhenTheCarGetsClose() {
        assertEquals(
            listOf<DriveCommand>(DriveCommand.Say("잠시 후 전망대입니다.")),
            started().onEvent(DriveEvent.LocationUpdated(near)),
        )
    }

    @Test
    fun nothingIsSaidFarFromASpot() {
        assertEquals(emptyList<DriveCommand>(), started().onEvent(DriveEvent.LocationUpdated(far)))
    }

    @Test
    fun aSpotIsAnnouncedOnlyOnce() {
        val guide = started()
        guide.onEvent(DriveEvent.LocationUpdated(near))
        assertEquals(emptyList<DriveCommand>(), guide.onEvent(DriveEvent.LocationUpdated(near)))
    }

    @Test
    fun aNewCourseCanAnnounceAgain() {
        val guide = started()
        guide.onEvent(DriveEvent.LocationUpdated(near))
        guide.onEvent(DriveEvent.CourseStarted(course))
        assertEquals(1, guide.onEvent(DriveEvent.LocationUpdated(near)).size)
    }

    @Test
    fun beforeACourseStartsNothingIsSaid() {
        assertEquals(emptyList<DriveCommand>(), GuideFeature().onEvent(DriveEvent.LocationUpdated(near)))
    }
}
