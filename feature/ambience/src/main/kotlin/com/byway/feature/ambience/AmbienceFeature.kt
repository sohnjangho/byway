package com.byway.feature.ambience

import com.byway.core.drive.CabinScene
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature

/** 부가 9: 코스 구간 분위기에 맞춰 차 안을 바꾼다. 주행 중 창문은 소폭만 연다. 코스가 끝나거나 멈추면 되돌린다. */
class AmbienceFeature : DriveFeature {
    override val id = "ambience"

    /** 연출이 차 안을 바꾼 적이 있는지. 바꾼 것이 없으면 되돌리라고 하지 않는다. */
    private var changed = false

    override fun onEvent(event: DriveEvent): List<DriveCommand> = when (event) {
        is DriveEvent.SectionEntered -> enter(event.section.mood)
        DriveEvent.CourseFinished, DriveEvent.CourseStopped, DriveEvent.CoursePaused -> restore()
        else -> emptyList()
    }

    private fun enter(mood: String?): List<DriveCommand> {
        val scene = SCENES[mood] ?: return emptyList()
        changed = true
        return listOf(DriveCommand.SetCabin(scene))
    }

    private fun restore(): List<DriveCommand> {
        if (!changed) return emptyList()
        changed = false
        return listOf(DriveCommand.RestoreCabin)
    }

    private companion object {
        val SCENES = mapOf(
            "forest" to CabinScene(windowPosition = 2, fanSpeed = 3, ambientBrightness = 40),
            "city" to CabinScene(windowPosition = 0, fanSpeed = 3, ambientBrightness = 30),
            "river" to CabinScene(windowPosition = 2, fanSpeed = 2, ambientBrightness = 70),
        )
    }
}
