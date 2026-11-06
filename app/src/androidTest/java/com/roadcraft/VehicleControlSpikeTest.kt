package com.roadcraft

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 3rd-party 앱 권한으로 차량 제어 명령이 실제 반영되는지 에뮬레이터에서 확인한다. */
@RunWith(AndroidJUnit4::class)
class VehicleControlSpikeTest {
    private lateinit var vehicle: Vehicle

    @Before
    fun setUp() {
        vehicle = Vehicle(InstrumentationRegistry.getInstrumentation().targetContext)
        vehicle.initialize()
        Thread.sleep(2_000)
    }

    @After
    fun tearDown() = vehicle.release()

    private fun <T> await(label: String, call: (onOk: (T) -> Unit, onErr: (Exception) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: Exception? = null
        call({ value = it; latch.countDown() }, { error = it; latch.countDown() })
        if (!latch.await(10, TimeUnit.SECONDS)) throw AssertionError("$label: 10초 내 응답 없음")
        error?.let { throw AssertionError("$label 실패: ${it::class.simpleName}: ${it.message}", it) }
        Log.i("SPIKE", "$label -> $value")
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    @Test
    fun windowPositionIsApplied() {
        val window = vehicle.getWindow()
        val area = await<WindowArea>("window area") { ok, err ->
            window.checkWindowPositionCapabilities({ _, bounds -> ok(bounds.keys.first()) }, err)
        }
        val before = await<Int?>("window before") { ok, err -> window.getCurrentWindowPosition(area, ok, err) }
        val target = if (before == 5) 7 else 5
        await<Unit>("setWindowPosition") { ok, err ->
            window.setWindowPosition(area, target, { _, _ -> ok(Unit) }, err)
        }
        Thread.sleep(3_000)
        val now = await<Int?>("getCurrentWindowPosition") { ok, err ->
            window.getCurrentWindowPosition(area, ok, err)
        }
        assertEquals(target, now)
    }

    @Test
    fun hvacFanSpeedIsApplied() {
        val hvac = vehicle.getHVAC()
        val powerArea = await<SeatArea>("hvac power area") { ok, err ->
            hvac.checkHvacPowerCapabilities({ _, areas -> ok(areas.first()) }, err)
        }
        val fanArea = await<SeatArea>("hvac fan area") { ok, err ->
            hvac.checkHvacFanSpeedCapabilities({ _, bounds -> ok(bounds.keys.first()) }, err)
        }
        await<Unit>("setHvacPower") { ok, err -> hvac.setHvacPower(powerArea, true, { _, _ -> ok(Unit) }, err) }
        val before = await<Int?>("fan before") { ok, err -> hvac.getHvacFanSpeed(fanArea, ok, err) }
        val target = if (before == 5) 6 else 5
        await<Unit>("setHvacFanSpeed") { ok, err -> hvac.setHvacFanSpeed(fanArea, target, { _, _ -> ok(Unit) }, err) }
        Thread.sleep(1_000)
        val now = await<Int?>("getHvacFanSpeed") { ok, err -> hvac.getHvacFanSpeed(fanArea, ok, err) }
        assertEquals(target, now)
    }

    @Test
    fun ambientBrightnessIsApplied() {
        val light = vehicle.getLight()
        val area = await<SeatArea>("ambient area") { ok, err ->
            light.checkAmbientLightBrightnessCapabilities({ _, bounds -> ok(bounds.keys.first()) }, err)
        }
        val before = await<Int?>("ambient before") { ok, err -> light.getAmbientLightBrightness(area, ok, err) }
        val target = if (before == 40) 60 else 40
        await<Unit>("setAmbientLightBrightness") { ok, err ->
            light.setAmbientLightBrightness(area, target, { _, _ -> ok(Unit) }, err)
        }
        Thread.sleep(1_000)
        val now = await<Int?>("getAmbientLightBrightness") { ok, err ->
            light.getAmbientLightBrightness(area, ok, err)
        }
        assertEquals(target, now)
    }

    @Test
    fun logCapabilities() {
        await<String>("window") { ok, err ->
            vehicle.getWindow().checkWindowPositionCapabilities({ c, m -> ok("$c $m") }, err)
        }
        await<String>("hvacPower") { ok, err ->
            vehicle.getHVAC().checkHvacPowerCapabilities({ c, m -> ok("$c $m") }, err)
        }
        await<String>("hvacFan") { ok, err ->
            vehicle.getHVAC().checkHvacFanSpeedCapabilities({ c, m -> ok("$c $m") }, err)
        }
        await<String>("ambient") { ok, err ->
            vehicle.getLight().checkAmbientLightBrightnessCapabilities({ c, m -> ok("$c $m") }, err)
        }
    }
}
