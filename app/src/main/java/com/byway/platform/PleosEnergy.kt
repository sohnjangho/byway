package com.byway.platform

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byway.core.course.EnergyRate
import com.byway.core.course.plausibleRate

private typealias Reader = (onValue: (Double?) -> Unit, onFailed: (Exception) -> Unit) -> Unit

/**
 * 이 차의 평균 연비나 전비를 읽는다. 연료차 값을 먼저 보고 없으면 전기차 값을 본다.
 * 지원하지 않는 차와 에뮬레이터(2026-11-09 실측: 조회가 모두 실패)에서는 아무것도 알리지 않고, 화면은 연료량 칸을 숨긴다.
 */
class PleosEnergy(context: Context, private val onRate: (EnergyRate) -> Unit) {
    private val vehicle = Vehicle(context)
    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0

    fun start() {
        vehicle.initialize()
        // initialize 직후에는 조회가 실패할 수 있다.
        handler.postDelayed(::read, READY_MS)
    }

    private fun read() {
        attempts++
        val fuel = vehicle.getFuel()
        val ev = vehicle.getEvBattery()
        val readers: List<Pair<Boolean, Reader>> = listOf(
            false to { ok, fail -> fuel.getCumulativeFuelEconomy({ ok(it?.toDouble()) }, fail) },
            false to { ok, fail -> fuel.getSinceChargeFuelEconomy({ ok(it?.toDouble()) }, fail) },
            true to { ok, fail -> ev.getCumulativeEvEfficiency({ ok(it?.toDouble()) }, fail) },
            true to { ok, fail -> ev.getSinceChargeEvEfficiency({ ok(it?.toDouble()) }, fail) },
        )
        tryNext(readers, 0)
    }

    private fun tryNext(readers: List<Pair<Boolean, Reader>>, index: Int) {
        val next = readers.getOrNull(index)
        if (next == null) {
            Log.i(TAG, "energy rate unavailable (try $attempts)")
            // 차량 연결이 늦을 수 있어 몇 번은 다시 읽는다.
            if (attempts < MAX_ATTEMPTS) handler.postDelayed(::read, RETRY_MS)
            return
        }
        val (electric, reader) = next
        reader(
            { value ->
                handler.post {
                    val rate = plausibleRate(electric, value)
                    Log.i(TAG, "energy electric=$electric value=$value usable=${rate != null}")
                    if (rate != null) onRate(rate) else tryNext(readers, index + 1)
                }
            },
            { failure ->
                Log.i(TAG, "energy read failed: $failure")
                handler.post { tryNext(readers, index + 1) }
            },
        )
    }

    private companion object {
        const val TAG = "DRIVE"
        const val READY_MS = 2_500L
        const val RETRY_MS = 10_000L
        const val MAX_ATTEMPTS = 3
    }
}
