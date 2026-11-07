package com.byway.core.drive

/**
 * 주행 규제를 판단하는 유일한 곳.
 * when 에 else 를 두지 않는다. 새 명령을 추가하면 컴파일이 멈추고 여기서 허용 여부를 정해야 한다.
 */
class DriveGate {
    private var parked = false
    private var mode = DriveMode.MANUAL

    fun onDrivingState(parked: Boolean) {
        this.parked = parked
    }

    fun onDriveMode(mode: DriveMode) {
        this.mode = mode
    }

    fun allows(command: DriveCommand): Boolean = when (command) {
        is DriveCommand.FollowRoute, is DriveCommand.Say, is DriveCommand.ShowLine, is DriveCommand.ShowProgress, is DriveCommand.SetCabin -> true
        is DriveCommand.ShowPanel -> parked || mode == DriveMode.EYES_OFF
        is DriveCommand.Publish -> false
    }
}
