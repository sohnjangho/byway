package com.byway.core.drive

/** 사건을 엔진과 부가 기능에 나눠 주고, 나온 명령을 규제에 통과시킨다. 한 스레드에서만 부른다. */
class DriveHost(
    private val engine: DriveFeature,
    features: List<DriveFeature>,
    private val onFeatureFailed: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val gate = DriveGate()
    private val active = features.toMutableList()

    fun dispatch(event: DriveEvent): List<DriveCommand> {
        val pending = ArrayDeque(listOf(event))
        val allowed = mutableListOf<DriveCommand>()
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (next is DriveEvent.DrivingStateChanged) gate.onDrivingState(next.parked)
            if (next is DriveEvent.DriveModeChanged) gate.onDriveMode(next.mode)
            for (command in engine.onEvent(next) + featureCommands(next)) {
                if (command is DriveCommand.Publish) pending += command.event
                if (gate.allows(command)) allowed += command
            }
        }
        return allowed
    }

    private fun featureCommands(event: DriveEvent): List<DriveCommand> =
        active.toList().flatMap { feature ->
            try {
                feature.onEvent(event)
            } catch (failure: Exception) {
                active -= feature
                onFeatureFailed(feature.id, failure)
                emptyList()
            }
        }
}
