package com.byway.feature.music

import com.byway.core.course.Section
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFeatureTest {
    private fun entered(mood: String?) = DriveEvent.SectionEntered(Section("구간", 0, 1, mood))

    @Test
    fun aSectionPlaysMusicForItsMood() {
        assertEquals(listOf<DriveCommand>(DriveCommand.PlayMusic("forest")), MusicFeature().onEvent(entered("forest")))
    }

    @Test
    fun theSameMoodKeepsPlaying() {
        val music = MusicFeature()
        music.onEvent(entered("river"))
        assertEquals(emptyList<DriveCommand>(), music.onEvent(entered("river")))
    }

    @Test
    fun aSectionWithoutMoodUsesCityMusic() {
        assertEquals(listOf<DriveCommand>(DriveCommand.PlayMusic("city")), MusicFeature().onEvent(entered(null)))
    }

    @Test
    fun theEndOfTheCourseStopsTheMusic() {
        assertEquals(listOf<DriveCommand>(DriveCommand.StopMusic), MusicFeature().onEvent(DriveEvent.CourseFinished))
    }

    @Test
    fun aNewCourseStartsItsMusicAgain() {
        val music = MusicFeature()
        music.onEvent(entered("river"))
        music.onEvent(DriveEvent.CourseFinished)
        assertEquals(1, music.onEvent(entered("river")).size)
    }
}
