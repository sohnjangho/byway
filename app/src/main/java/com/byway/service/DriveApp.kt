package com.byway.service

import android.app.Application
import com.byway.platform.PleosGear

/** 코스를 달리기 전에도 화면이 정차 여부를 알도록 앱 프로세스 동안 기어를 읽는다. */
class DriveApp : Application() {
    private lateinit var gear: PleosGear

    override fun onCreate() {
        super.onCreate()
        gear = PleosGear(this) { parked -> DriveBoard.update { it.copy(parked = parked) } }
        gear.start()
    }
}
