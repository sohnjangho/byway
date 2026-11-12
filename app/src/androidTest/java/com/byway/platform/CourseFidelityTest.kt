package com.byway.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byway.core.course.Leg
import com.byway.core.course.Stop
import com.byway.core.course.parseCourse
import com.byway.core.engine.planLegs
import com.byway.core.engine.planStops
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** 첫 구간을 내비에 올려, 내비가 계산한 거리가 코스 원본과 얼마나 다른지 잰다. */
@RunWith(AndroidJUnit4::class)
class CourseFidelityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val results = LinkedBlockingQueue<String>()
    private val details = LinkedBlockingQueue<String>()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { results += routeStartInfo.toString() }
        override fun onError(errorCode: NaviErrorCode) { results += "error $errorCode" }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { details += "dest $destinationInfo" }
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) { details += "via $waypointInfo" }
    }

    private fun Stop.info() = RouteInfo(
        latitude = point.lat, longitude = point.lon, poiName = name, poiId = "0", address = name, poiSubId = "0",
    )
    private fun Stop.via(index: WaypointIndex) = RequestWaypointInfo(
        latitude = point.lat, longitude = point.lon, poiName = name, waypointIndex = index,
        poiId = "0", address = name, poiSubId = "0",
    )

    private fun NaviHelper.put(leg: Leg) {
        val indexes = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
        requestRouteWithWaypoints(
            RouteWithWaypointsRequest(leg.destination.info(), leg.waypoints.mapIndexed { i, s -> s.via(indexes[i]) }),
        )
    }

    /** -e course <파일 이름> 으로 코스를 고르고 -e tolerances 800,1200 으로 값을 고른다. */
    @Test
    fun measureFirstLegAtEachTolerance() {
        val arguments = InstrumentationRegistry.getArguments()
        val file = arguments.getString("course") ?: "paldang-sunset.json"
        val text = context.assets.open("courses/$file").bufferedReader().use { it.readText() }
        val course = parseCourse(text)
        val tolerances = arguments.getString("tolerances")?.split(",")?.map { it.toDouble() }
            ?: listOf(100.0, 200.0, 300.0, 500.0, 800.0, 1_200.0, 2_000.0)
        val navi = NaviHelper(context)
        val connected = CountDownLatch(1)
        navi.initialize(onConnected = { connected.countDown() }, onFailed = { connected.countDown() })
        assertTrue(connected.await(15, TimeUnit.SECONDS))
        navi.addListener(listener)
        for (tolerance in tolerances) {
            val stops = planStops(course, tolerance)
            val leg = planLegs(stops).first()
            val courseMeters = course.path.take(leg.destination.pathIndex + 1).zipWithNext { a, b -> a.distanceTo(b) }.sum()
            results.clear()
            navi.put(leg)
            val result = results.poll(30, TimeUnit.SECONDS)
            Thread.sleep(3_000)
            details.clear()
            navi.getDestinationInfo()
            navi.getWaypointInfo()
            val first = details.poll(10, TimeUnit.SECONDS)
            val second = details.poll(5, TimeUnit.SECONDS)
            Log.i("FIDELITY", "course=${course.id} tol=$tolerance stops=${stops.size} legEnd=${leg.destination.pathIndex} courseM=${courseMeters.toInt()} start=$result")
            Log.i("FIDELITY", "  $first")
            Log.i("FIDELITY", "  $second")
        }
        navi.cancelRoute()
        Thread.sleep(1_000)
        navi.removeListener(listener)
        navi.release()
    }
}
