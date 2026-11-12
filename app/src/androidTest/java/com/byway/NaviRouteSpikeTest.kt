package com.byway

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 코스 실행이 NaviHelper로 가능한지 에뮬레이터에서 확인한다. 좌표는 기획서의 제주 코스다. */
@RunWith(AndroidJUnit4::class)
class NaviRouteSpikeTest {
    private lateinit var navi: NaviHelper
    private val events = CopyOnWriteArrayList<String>()
    private var waypoints: List<WaypointInfo> = emptyList()
    private var destination: DestinationInfo? = null

    private fun record(event: String) {
        Log.i("SPIKE", "navi $event")
        events += event
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = record("started $routeStartInfo")
        override fun onRoutingCompleted() = record("completed")
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            destination = destinationInfo
            record("destinationInfo $destinationInfo")
        }
        override fun onError(errorCode: NaviErrorCode) = record("error $errorCode")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            record("destinationArrived $destinationArrivedInfo")
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) =
            record("waypointArrived $waypointArrivedInfo")
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            waypoints = waypointInfo
            record("waypointInfo ${waypointInfo.size} $waypointInfo")
        }
    }

    private fun dest(name: String, lat: Double, lon: Double) = RouteInfo(
        latitude = lat, longitude = lon, poiName = name, poiId = "0", address = name, poiSubId = "0",
    )

    private fun via(name: String, lat: Double, lon: Double, index: WaypointIndex) = RequestWaypointInfo(
        latitude = lat, longitude = lon, poiName = name, waypointIndex = index,
        poiId = "0", address = name, poiSubId = "0",
    )

    private fun waitFor(seconds: Long, prefix: String): Boolean {
        repeat((seconds * 2).toInt()) {
            if (events.any { it.startsWith(prefix) || it.startsWith("error") }) return events.any { it.startsWith(prefix) }
            Thread.sleep(500)
        }
        return false
    }

    @Before
    fun setUp() {
        navi = NaviHelper(InstrumentationRegistry.getInstrumentation().targetContext)
        val connected = CountDownLatch(1)
        var failure: Throwable? = null
        navi.initialize(onConnected = { connected.countDown() }, onFailed = { failure = it; connected.countDown() })
        assertTrue("연결 응답 없음", connected.await(15, TimeUnit.SECONDS))
        failure?.let { throw AssertionError("NaviHelper 연결 실패: $it", it) }
        navi.addListener(listener)
    }

    @After
    fun tearDown() {
        navi.cancelRoute()
        Thread.sleep(1_000)
        navi.removeListener(listener)
        navi.release()
    }

    @Test
    fun routeWithTwoWaypointsStarts() {
        navi.requestRouteWithWaypoints(
            RouteWithWaypointsRequest(
                destination = dest("협재해수욕장", 33.3941, 126.2397),
                waypoints = listOf(
                    via("곽지해수욕장", 33.4503, 126.3047, WaypointIndex.FIRST),
                    via("한림공원", 33.3895, 126.2395, WaypointIndex.SECOND),
                ),
            ),
        )
        assertTrue("경로 시작 이벤트 없음: $events", waitFor(30, "started"))
        // 앞 시험의 cancelRoute 가 늦게 보낸 빈 경유지 알림이 남아 있으면 조회 응답으로 오인한다.
        events.clear()
        navi.getWaypointInfo()
        assertTrue("경유지 조회 없음: $events", waitFor(10, "waypointInfo"))
        assertEquals(2, waypoints.size)
    }

    @Test
    fun nextSegmentReplacesCurrentRoute() {
        navi.requestRoute(dest("협재해수욕장", 33.3941, 126.2397))
        assertTrue("1구간 시작 없음: $events", waitFor(30, "started"))
        events.clear()
        navi.requestRouteWithWaypoints(
            RouteWithWaypointsRequest(
                destination = dest("김녕해수욕장", 33.5574, 126.7596),
                waypoints = listOf(via("금능해수욕장", 33.3899, 126.2356, WaypointIndex.FIRST)),
            ),
        )
        assertTrue("2구간 시작 없음: $events", waitFor(30, "started"))
    }

    /** 판교 고정 출발. 같은 목적지에 경유지를 넣으면 경로 총거리가 바뀌는지 측정한다. */
    @Test
    fun waypointForcesDifferentRoute() {
        val target = dest("두물머리", 37.5336, 127.3142)
        navi.requestRoute(target)
        assertTrue("직행 시작 없음: $events", waitFor(30, "started"))
        Thread.sleep(2_000)
        events.clear()
        navi.getDestinationInfo()
        assertTrue("직행 목적지 정보 없음: $events", waitFor(10, "destinationInfo"))
        val direct = destination.toString()
        events.clear()
        navi.requestRouteWithWaypoints(
            RouteWithWaypointsRequest(
                destination = target,
                waypoints = listOf(via("남한산성", 37.4789, 127.1822, WaypointIndex.FIRST)),
            ),
        )
        assertTrue("경유 시작 없음: $events", waitFor(30, "started"))
        Thread.sleep(2_000)
        events.clear()
        navi.getDestinationInfo()
        assertTrue("경유 목적지 정보 없음: $events", waitFor(10, "destinationInfo"))
        Log.i("SPIKE", "navi compare direct=$direct via=$destination")
        assertTrue("경유지가 경로를 바꾸지 않음", direct != destination.toString())
    }
}
