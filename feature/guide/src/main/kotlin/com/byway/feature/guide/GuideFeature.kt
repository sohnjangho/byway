package com.byway.feature.guide

import com.byway.core.course.Spot
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature
import com.byway.core.geo.GeoPoint

/** 코스 음성 안내: 볼거리에 가까워지면 한 번 알려 준다. */
class GuideFeature : DriveFeature {
    override val id = "guide"
    private var waiting = emptyList<Spot>()

    override fun onEvent(event: DriveEvent): List<DriveCommand> = when (event) {
        is DriveEvent.CourseStarted -> {
            waiting = event.course.spots
            emptyList()
        }
        is DriveEvent.LocationUpdated -> announce(event.point)
        else -> emptyList()
    }

    private fun announce(point: GeoPoint): List<DriveCommand> {
        val close = waiting.filter { it.point.distanceTo(point) <= NEAR_METERS }
        waiting = waiting - close.toSet()
        return close.map { DriveCommand.Say("잠시 후 ${it.name}입니다.") }
    }

    private companion object {
        const val NEAR_METERS = 800.0
    }
}
