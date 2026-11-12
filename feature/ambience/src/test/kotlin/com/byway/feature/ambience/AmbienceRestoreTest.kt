package com.byway.feature.ambience

import com.byway.core.course.Section
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbienceRestoreTest {
    private val river = DriveEvent.SectionEntered(Section("강변", 0, 1, "river"))
    private fun changed() = AmbienceFeature().also { it.onEvent(river) }

    @Test
    fun finishingPutsTheCabinBack() {
        assertEquals(listOf<DriveCommand>(DriveCommand.RestoreCabin), changed().onEvent(DriveEvent.CourseFinished))
    }

    /** 운전자가 코스를 끝내거나, 다른 목적지로 가느라 코스가 멈춰도 되돌린다. */
    @Test
    fun stoppingAndPausingPutTheCabinBackToo() {
        assertEquals(listOf<DriveCommand>(DriveCommand.RestoreCabin), changed().onEvent(DriveEvent.CourseStopped))
        assertEquals(listOf<DriveCommand>(DriveCommand.RestoreCabin), changed().onEvent(DriveEvent.CoursePaused))
    }

    @Test
    fun nothingChangedMeansNothingToPutBack() {
        val feature = AmbienceFeature()
        feature.onEvent(DriveEvent.SectionEntered(Section("구간", 0, 1, null)))
        assertTrue(feature.onEvent(DriveEvent.CourseFinished).isEmpty())
    }

    @Test
    fun theCabinIsPutBackOnlyOnce() {
        val feature = changed()
        feature.onEvent(DriveEvent.CoursePaused)
        assertTrue(feature.onEvent(DriveEvent.CourseStopped).isEmpty())
    }

    /** 멈췄던 코스를 이어가면 구간이 다시 알려지고 연출도 다시 들어간다. */
    @Test
    fun resumingChangesTheCabinAgainAndPutsItBackAtTheEnd() {
        val feature = changed()
        feature.onEvent(DriveEvent.CoursePaused)
        assertTrue(feature.onEvent(river).single() is DriveCommand.SetCabin)
        assertEquals(listOf<DriveCommand>(DriveCommand.RestoreCabin), feature.onEvent(DriveEvent.CourseFinished))
    }
}
