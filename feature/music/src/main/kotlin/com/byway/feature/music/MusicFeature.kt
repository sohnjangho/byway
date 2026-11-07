package com.byway.feature.music

import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature

/** 배경음악: 구간 분위기에 맞는 곡을 고른다. 재생은 platform 이 맡고 안내 음성이 우선한다. */
class MusicFeature : DriveFeature {
    override val id = "music"
    private var playing: String? = null

    override fun onEvent(event: DriveEvent): List<DriveCommand> = when (event) {
        is DriveEvent.SectionEntered -> play(event.section.mood ?: DEFAULT_MOOD)
        DriveEvent.CourseFinished -> {
            playing = null
            listOf(DriveCommand.StopMusic)
        }
        else -> emptyList()
    }

    private fun play(mood: String): List<DriveCommand> {
        if (mood == playing) return emptyList()
        playing = mood
        return listOf(DriveCommand.PlayMusic(mood))
    }

    private companion object {
        const val DEFAULT_MOOD = "city"
    }
}
