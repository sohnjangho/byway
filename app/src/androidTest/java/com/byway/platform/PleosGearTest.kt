package com.byway.platform

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byway.service.DriveBoard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Virtual Console 로 기어를 바꿔 값이 따라오는지 잰다. 콘솔은 에뮬레이터 안에 있어 소켓으로 바로 붙는다. */
@RunWith(AndroidJUnit4::class)
class PleosGearTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val session = Executors.newSingleThreadScheduledExecutor()
    private val reported = LinkedBlockingQueue<Boolean>()
    private val offMain = LinkedBlockingQueue<String>()
    private lateinit var gear: PleosGear
    private lateinit var original: String

    private fun console(method: String, path: String): String =
        Socket("127.0.0.1", 8088).use { socket ->
            socket.soTimeout = 3_000
            socket.getOutputStream()
                .write("$method $path HTTP/1.0\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n".toByteArray())
            socket.getInputStream().readBytes().decodeToString()
        }

    private fun shift(name: String) {
        val reply = console("PUT", "/vehicle/gear?gear=$name")
        assertTrue(reply, reply.startsWith("HTTP/1.1 200"))
    }

    private fun startGear() {
        gear = PleosGear(context) {
            if (Looper.myLooper() != Looper.getMainLooper()) offMain += Thread.currentThread().name
            reported += it
        }
        gear.start()
    }

    private fun next(): Boolean? = reported.poll(WAIT_S, TimeUnit.SECONDS)

    private fun awaitBoard(parked: Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_S * 1_000
        while (DriveBoard.state.parked != parked && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(parked, DriveBoard.state.parked)
    }

    /** 시뮬레이터는 세션 신호가 3초 끊기면 꺼진다. */
    @Before
    fun setUp() {
        original = requireNotNull(Regex("\"actual\":\\{\"value\":\"(\\w+)\"").find(console("GET", "/vehicle/state"))).groupValues[1]
        session.scheduleAtFixedRate({ runCatching { console("POST", "/browser/session?client=byway-test") } }, 0, 1, TimeUnit.SECONDS)
        Thread.sleep(3_000)
    }

    @After
    fun tearDown() {
        shift(original)
        session.shutdownNow()
        assertNull(offMain.peek())
    }

    @Test
    fun parkIsParked() {
        shift("Drive")
        startGear()
        assertEquals(false, next())
        shift("Park")
        assertEquals(true, next())
    }

    @Test
    fun driveIsDriving() {
        shift("Park")
        startGear()
        assertEquals(true, next())
        shift("Drive")
        assertEquals(false, next())
    }

    @Test
    fun otherGearsAreDrivingAndRepeatNothing() {
        shift("Drive")
        startGear()
        assertEquals(false, next())
        shift("Reverse")
        shift("Neutral")
        assertNull(reported.poll(2, TimeUnit.SECONDS))
    }

    /** 코스를 달리기 전에도 화면이 정차 여부를 알아야 한다. */
    @Test
    fun boardFollowsGearWithoutCourse() {
        shift("Drive")
        awaitBoard(parked = false)
        shift("Park")
        awaitBoard(parked = true)
        shift("Drive")
        awaitBoard(parked = false)
    }

    /** 코스가 끝나면 PleosCabin 이 해제된다. 그 뒤에도 앱의 기어 구독은 살아 있어야 한다. */
    @Test
    fun boardFollowsGearAfterCabinRelease() {
        PleosCabin(context).run {
            start()
            Thread.sleep(500)
            release()
        }
        shift("Drive")
        awaitBoard(parked = false)
        shift("Park")
        awaitBoard(parked = true)
        shift("Drive")
        awaitBoard(parked = false)
    }

    /** 알림이 조용히 끊겨도 정차로 굳으면 안 된다. 주행 중 규제가 풀리기 때문이다. */
    @Test
    fun gearStillFollowsAfterNotificationsStop() {
        shift("Park")
        startGear()
        assertEquals(true, next())
        val silent = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = Unit
            override fun onFailed(e: Exception) = Unit
        }
        Vehicle(context).run {
            initialize()
            Thread.sleep(1_000)
            getGear().unregisterGearSelection(silent)
        }
        Thread.sleep(500)
        shift("Drive")
        assertEquals(false, next())
    }

    private companion object {
        const val WAIT_S = 5L
    }
}
