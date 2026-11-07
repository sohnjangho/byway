package com.byway.feature.ambience

import com.byway.core.course.Section
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbienceFeatureTest {
    private fun enter(mood: String?) = AmbienceFeature().onEvent(DriveEvent.SectionEntered(Section("구간", 0, 1, mood)))
    private fun scene(mood: String) = (enter(mood).single() as DriveCommand.SetCabin).scene

    @Test
    fun riverOpensTheWindowALittle() {
        assertEquals(2, scene("river").windowPosition)
    }

    @Test
    fun cityClosesTheWindow() {
        assertEquals(0, scene("city").windowPosition)
    }

    @Test
    fun noMoodChangesNothing() {
        assertTrue(enter(null).isEmpty())
    }

    @Test
    fun unknownMoodChangesNothing() {
        assertTrue(enter("space").isEmpty())
    }

    @Test
    fun windowNeverOpensWideWhileDriving() {
        listOf("forest", "city", "river").forEach { assertTrue((scene(it).windowPosition ?: 0) <= 3) }
    }

    @Test
    fun otherEventsChangeNothing() {
        assertTrue(AmbienceFeature().onEvent(DriveEvent.CourseFinished).isEmpty())
    }
}
