package com.byway.core.drive

/**
 * 주행 규제를 판단하는 유일한 곳.
 * when 에 else 를 두지 않는다. 새 명령을 추가하면 컴파일이 멈추고 여기서 허용 여부를 정해야 한다.
 */
class DriveGate {
    private var parked = false

    fun onDrivingState(parked: Boolean) {
        this.parked = parked
    }

    fun allows(command: DriveCommand): Boolean = when (command) {
        is DriveCommand.ShowRoute, is DriveCommand.Say, is DriveCommand.ShowLine -> true
        is DriveCommand.ShowPanel -> parked
        is DriveCommand.Publish -> false
    }
}
