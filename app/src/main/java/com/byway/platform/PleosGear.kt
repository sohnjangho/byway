package com.byway.platform

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 기어를 정차 여부로 바꿔 메인 스레드로 넘긴다. P단만 정차이고, 그 외와 값을 모를 때는 주행이다.
 * 해제 함수를 두지 않는다. unregisterGearSelection 은 한 곳에서만 불러도 프로세스 전체의 알림을 끊는다.
 */
class PleosGear(context: Context, private val onParked: (Boolean) -> Unit) {
    private val vehicle = Vehicle(context)
    private val handler = Handler(Looper.getMainLooper())
    private var last: Boolean? = null

    private val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = report(gear)
        override fun onFailed(e: Exception) = fail(e)
    }

    fun start() {
        vehicle.initialize()
        val gear = vehicle.getGear()
        gear.registerGearSelection(listener)
        gear.getCurrentGearSelectionState(::report, ::fail)
    }

    private fun report(gear: VehicleGear?) {
        val parked = gear == VehicleGear.GEAR_PARK
        handler.post {
            if (parked != last) {
                last = parked
                onParked(parked)
            }
        }
    }

    private fun fail(failure: Exception) {
        Log.w("DRIVE", "gear unknown: $failure")
        report(null)
    }
}
