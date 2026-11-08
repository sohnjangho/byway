package com.byway.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 플랫폼 주행 규제를 메인 스레드로 넘긴다. 값을 얻지 못하면 null 을 넘겨 기어로 판단하게 한다. */
class CarRestrictions(private val context: Context, private val onChange: (Boolean?) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null

    fun start() {
        runCatching {
            val connected = requireNotNull(Car.createCar(context)) { "car service unavailable" }
            car = connected
            val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager.registerListener { report(it) }
            report(manager.currentCarUxRestrictions)
        }.onFailure {
            Log.w(TAG, "restrictions unknown: $it")
            handler.post { onChange(null) }
        }
    }

    private fun report(restrictions: CarUxRestrictions) {
        val required = restrictions.isRequiresDistractionOptimization
        handler.post { onChange(required) }
    }

    private companion object {
        const val TAG = "DRIVE"
    }
}
