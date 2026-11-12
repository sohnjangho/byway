package com.byway.platform

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import android.content.Context
import android.util.Log
import com.byway.core.drive.CabinScene

/** 창문·풍량·조명을 바꾼다. 차종이 지원하지 않는 항목은 건너뛴다. */
class PleosCabin(context: Context) {
    private val vehicle = Vehicle(context)
    private var window: WindowArea? = null
    private var fan: SeatArea? = null
    private var hvacPower: SeatArea? = null
    private var light: SeatArea? = null

    fun start() {
        vehicle.initialize()
    }

    /** initialize 직후에는 조회가 실패할 수 있어 첫 장면을 적용할 때 지원 범위를 확인한다. */
    fun apply(scene: CabinScene) {
        scene.windowPosition?.let(::setWindow)
        scene.fanSpeed?.let(::setFan)
        scene.ambientBrightness?.let(::setLight)
    }

    private fun setWindow(position: Int) {
        val api = vehicle.getWindow()
        val apply = { area: WindowArea -> api.setWindowPosition(area, position, { _, _ -> }, ::skip) }
        window?.let(apply) ?: api.checkWindowPositionCapabilities(
            { _, bounds -> bounds.keys.firstOrNull()?.let { window = it; apply(it) } }, ::skip,
        )
    }

    private fun setFan(speed: Int) {
        val api = vehicle.getHVAC()
        val apply = { area: SeatArea -> api.setHvacFanSpeed(area, speed, { _, _ -> }, ::skip) }
        if (hvacPower == null) {
            api.checkHvacPowerCapabilities(
                { _, areas -> areas.firstOrNull()?.let { hvacPower = it; api.setHvacPower(it, true, { _, _ -> }, ::skip) } }, ::skip,
            )
        }
        fan?.let(apply) ?: api.checkHvacFanSpeedCapabilities(
            { _, bounds -> bounds.keys.firstOrNull()?.let { fan = it; apply(it) } }, ::skip,
        )
    }

    private fun setLight(brightness: Int) {
        val api = vehicle.getLight()
        val apply = { area: SeatArea -> api.setAmbientLightBrightness(area, brightness, { _, _ -> }, ::skip) }
        light?.let(apply) ?: api.checkAmbientLightBrightnessCapabilities(
            { _, bounds -> bounds.keys.firstOrNull()?.let { light = it; apply(it) } }, ::skip,
        )
    }

    private fun skip(failure: Exception) {
        Log.w("DRIVE", "cabin skipped: $failure")
    }

    fun release() = vehicle.release()
}
