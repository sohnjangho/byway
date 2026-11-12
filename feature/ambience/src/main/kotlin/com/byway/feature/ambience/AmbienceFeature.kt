package com.byway.feature.ambience

import com.byway.core.drive.CabinScene
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature

/** 부가 9: 코스 구간 분위기에 맞춰 차 안을 바꾼다. 주행 중 창문은 소폭만 연다. */
class AmbienceFeature : DriveFeature {
    override val id = "ambience"

    override fun onEvent(event: DriveEvent): List<DriveCommand> {
        val section = (event as? DriveEvent.SectionEntered)?.section ?: return emptyList()
        val scene = SCENES[section.mood] ?: return emptyList()
        return listOf(DriveCommand.SetCabin(scene))
    }

    private companion object {
        val SCENES = mapOf(
            "forest" to CabinScene(windowPosition = 2, fanSpeed = 3, ambientBrightness = 40),
            "city" to CabinScene(windowPosition = 0, fanSpeed = 3, ambientBrightness = 30),
            "river" to CabinScene(windowPosition = 2, fanSpeed = 2, ambientBrightness = 70),
        )
    }
}
