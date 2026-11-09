package com.byway.platform

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byway.core.drive.CabinScene

/**
 * 창문·풍량·조명을 바꾸고, 코스가 끝나면 되돌린다. 차종이 지원하지 않는 항목은 건너뛴다.
 * 항목을 처음 바꾸기 직전에 지금 값을 읽어 둔다. 되돌릴 때 지금 값이 우리가 마지막에 쓴 값과 다르면
 * 운전자가 직접 바꾼 것이므로 그 항목은 건드리지 않는다([CabinMemory]).
 */
class PleosCabin(context: Context, private val onRemembered: (String) -> Unit = {}) {
    private val vehicle = Vehicle(context)
    private var window: WindowArea? = null
    private var fan: SeatArea? = null
    private var hvacPower: SeatArea? = null
    private var light: SeatArea? = null
    private var memory = CabinMemory()
    private val main = Handler(Looper.getMainLooper())

    /** 되돌릴 때마다 늘어난다. 되돌린 뒤에 도착한 읽기 결과로 연출을 다시 쓰지 않게 한다. */
    private var generation = 0

    fun start() {
        vehicle.initialize()
    }

    /** 앱이 꺼지기 전에 남겨 둔 기억을 되살린다. 이어 달리거나 그만둘 때 처음 값으로 되돌릴 수 있다. */
    fun remember(saved: String) {
        memory = CabinMemory.decode(saved)
    }

    /** initialize 직후에는 조회가 실패할 수 있어 첫 장면을 적용할 때 지원 범위를 확인한다. */
    fun apply(scene: CabinScene) {
        scene.windowPosition?.let { change(CabinItem.WINDOW, it) }
        scene.fanSpeed?.let { change(CabinItem.FAN, it) }
        scene.ambientBrightness?.let { change(CabinItem.LIGHT, it) }
    }

    /** 연출이 바꾼 항목을 처음 값으로 되돌린다. 운전자가 그 뒤 직접 바꾼 항목은 둔다. */
    fun restore() {
        val past = memory
        memory = CabinMemory()
        generation++
        onRemembered("")
        past.items.forEach { item ->
            read(item) { now ->
                val back = past.restores(item, now)
                Log.i(TAG, "cabin restore $item now=$now back=$back")
                back?.let { write(item, it) }
            }
        }
    }

    /** 처음 바꾸는 항목은 지금 값을 먼저 읽어 둔다. 읽지 못하면 되돌릴 수 없으므로 기억하지 않고 쓰기만 한다. */
    private fun change(item: CabinItem, value: Int) {
        if (memory.knows(item)) {
            wrote(item, value, value)
            write(item, value)
            return
        }
        val asked = generation
        read(item, onFailed = { if (asked == generation) write(item, value) }) { now ->
            if (asked == generation) {
                wrote(item, now, value)
                write(item, value)
            }
        }
    }

    private fun wrote(item: CabinItem, was: Int, value: Int) {
        memory.wrote(item, was, value)
        onRemembered(memory.encode())
    }

    /** SDK 콜백은 다른 스레드로 올 수 있다. 기억을 만지는 일은 메인 스레드에서만 한다. */
    private fun read(item: CabinItem, onFailed: () -> Unit = {}, onValue: (Int) -> Unit) {
        val none = { main.post { onFailed() }; Unit }
        val fail = { failure: Exception ->
            skip(failure)
            none()
        }
        // 값을 모르면 null 이 온다. 읽지 못한 것과 같다.
        val got = { value: Int? -> main.post { value?.let(onValue) ?: onFailed() }; Unit }
        when (item) {
            CabinItem.WINDOW -> withWindow(none) { area -> vehicle.getWindow().getCurrentWindowPosition(area, got, fail) }
            CabinItem.FAN -> withFan(none) { area -> vehicle.getHVAC().getHvacFanSpeed(area, got, fail) }
            CabinItem.LIGHT -> withLight(none) { area -> vehicle.getLight().getAmbientLightBrightness(area, got, fail) }
        }
    }

    private fun write(item: CabinItem, value: Int) {
        when (item) {
            CabinItem.WINDOW -> withWindow { area -> vehicle.getWindow().setWindowPosition(area, value, { _, _ -> }, ::skip) }
            CabinItem.FAN -> {
                powerOn()
                withFan { area -> vehicle.getHVAC().setHvacFanSpeed(area, value, { _, _ -> }, ::skip) }
            }
            CabinItem.LIGHT -> withLight { area -> vehicle.getLight().setAmbientLightBrightness(area, value, { _, _ -> }, ::skip) }
        }
    }

    private fun withWindow(onNone: () -> Unit = {}, action: (WindowArea) -> Unit) {
        window?.let(action) ?: vehicle.getWindow().checkWindowPositionCapabilities(
            { _, bounds -> bounds.keys.firstOrNull()?.let { window = it; action(it) } ?: onNone() },
            { failure -> skip(failure); onNone() },
        )
    }

    private fun withFan(onNone: () -> Unit = {}, action: (SeatArea) -> Unit) {
        fan?.let(action) ?: vehicle.getHVAC().checkHvacFanSpeedCapabilities(
            { _, bounds -> bounds.keys.firstOrNull()?.let { fan = it; action(it) } ?: onNone() },
            { failure -> skip(failure); onNone() },
        )
    }

    private fun withLight(onNone: () -> Unit = {}, action: (SeatArea) -> Unit) {
        light?.let(action) ?: vehicle.getLight().checkAmbientLightBrightnessCapabilities(
            { _, bounds -> bounds.keys.firstOrNull()?.let { light = it; action(it) } ?: onNone() },
            { failure -> skip(failure); onNone() },
        )
    }

    /** 풍량은 공조가 켜져 있어야 바뀐다. */
    private fun powerOn() {
        if (hvacPower != null) return
        val api = vehicle.getHVAC()
        api.checkHvacPowerCapabilities(
            { _, areas -> areas.firstOrNull()?.let { hvacPower = it; api.setHvacPower(it, true, { _, _ -> }, ::skip) } }, ::skip,
        )
    }

    private fun skip(failure: Exception) {
        Log.w(TAG, "cabin skipped: $failure")
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        vehicle.release()
    }

    private companion object {
        const val TAG = "DRIVE"
    }
}
