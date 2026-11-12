package com.byway.service

import android.app.Application
import android.util.Log
import com.byway.platform.CarRestrictions
import com.byway.platform.PleosEnergy
import com.byway.platform.PleosGear

/** 코스를 달리기 전에도 화면이 정차 여부를 알도록 앱 프로세스 동안 기어와 주행 규제를 읽는다. */
class DriveApp : Application() {
    private lateinit var gear: PleosGear
    private lateinit var restrictions: CarRestrictions
    private var gearParked = false
    private var requiresDo: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        MyDrives.restore(this)
        gear = PleosGear(this) { parked ->
            gearParked = parked
            publish()
        }
        restrictions = CarRestrictions(this) { required ->
            requiresDo = required
            publish()
        }
        gear.start()
        PleosEnergy(this) { rate -> DriveBoard.update { it.copy(energy = rate) } }.start()
        restrictions.start()
    }

    private fun publish() {
        val parked = parkedFrom(gearParked, requiresDo)
        Log.i("DRIVE", "gearParked=$gearParked requiresDo=$requiresDo parked=$parked")
        DriveBoard.update { withParked(it, parked) }
    }
}
