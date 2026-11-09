package com.byway.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.byway.core.course.Leg
import com.byway.core.course.Stop
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.RouteAbility
import com.byway.core.drive.RouteFollower
import com.byway.core.geo.GeoPoint

/**
 * 차의 내장 내비에 구간을 올린다. 운전자가 내비를 직접 만지면(다른 목적지, 경유지 추가, 안내 종료) [onDriver]로 알리고,
 * 끝낼 때는 우리가 올린 경로만 지운다. 누구의 경로인지는 [RouteLedger]가 가린다.
 */
class PleosNavi(context: Context, private val onDriver: (DriveEvent) -> Unit) : RouteFollower {
    override val ability = RouteAbility()
    private val navi = NaviHelper(context)
    private val main = Handler(Looper.getMainLooper())
    private val ledger = RouteLedger()
    private var connected = false
    private var waiting: Leg? = null

    /** 이벤트는 다른 스레드로 온다. 판단은 메인 스레드에서만 한다. */
    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            main.post {
                Log.i(TAG, "route started ${routeStartInfo.name}")
                ledger.started()
                // 시작 이벤트에는 좌표가 없다. 목적지를 물어 누구의 경로인지 본다.
                navi.getDestinationInfo()
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val point = GeoPoint(destinationInfo.latitude, destinationInfo.longitude)
            main.post { destination(point) }
        }

        override fun onRouteCancelled() {
            main.post(::cancelled)
        }

        /** 우리 요청에는 오지 않는다(실측). 운전자가 내비에서 경유지를 더했을 때 온다. */
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            val added = "${waypointChangedInfo.cmdType}" == WAYPOINT_ADDED
            main.post { if (added) takenOver("waypoint added") }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            main.post(::arrived)
        }

        override fun onError(errorCode: NaviErrorCode) {
            main.post {
                Log.w(TAG, "navi error $errorCode")
                ledger.failed()
            }
        }
    }

    fun connect(onFailed: (Throwable) -> Unit) {
        navi.initialize(
            onConnected = {
                connected = true
                navi.addListener(listener)
                waiting?.let(::show)
                waiting = null
            },
            onFailed = onFailed,
        )
    }

    override fun follow(route: DriveCommand.FollowRoute) = show(route.leg)

    private fun show(leg: Leg) {
        if (!connected) {
            waiting = leg
            return
        }
        Log.i(TAG, "show leg via=${leg.waypoints.map { it.pathIndex }} to=${leg.destination.pathIndex}")
        ledger.requested(leg.destination.point, SystemClock.elapsedRealtime())
        if (leg.waypoints.isEmpty()) {
            navi.requestRoute(leg.destination.toRouteInfo())
        } else {
            navi.requestRouteWithWaypoints(
                RouteWithWaypointsRequest(
                    destination = leg.destination.toRouteInfo(),
                    waypoints = leg.waypoints.mapIndexed { i, stop -> stop.toWaypoint(INDEXES[i]) },
                ),
            )
        }
    }

    /** 경로가 꺼질 때는 묻지 않아도 (0, 0)이 온다. 목적지가 없다는 뜻이다. */
    private fun destination(point: GeoPoint) {
        if (point.lat == 0.0 && point.lon == 0.0) return
        if (!ledger.seen(point)) takenOver("destination ${point.lat},${point.lon}")
    }

    private fun cancelled() {
        val ours = ledger.cancelIsOurs(SystemClock.elapsedRealtime())
        Log.i(TAG, if (ours) "route replaced by ours" else "driver cancelled the route")
        if (!ours) onDriver(DriveEvent.RouteCancelled)
    }

    private fun takenOver(why: String) {
        Log.i(TAG, "driver took over: $why")
        onDriver(DriveEvent.RouteTakenOver)
    }

    private fun arrived() {
        if (ledger.oursIsActive) {
            Log.i(TAG, "arrived at our destination")
            ledger.arrived(SystemClock.elapsedRealtime())
        } else {
            Log.i(TAG, "arrived at the driver's destination")
            onDriver(DriveEvent.DestinationArrived)
        }
    }

    /** 운전자가 넣은 경로는 지우지 않는다. */
    fun release() {
        main.removeCallbacksAndMessages(null)
        Log.i(TAG, "release navi, our route active=${ledger.oursIsActive}")
        if (connected) {
            if (ledger.oursIsActive) navi.cancelRoute()
            navi.removeListener(listener)
        }
        navi.release()
    }

    private fun Stop.toRouteInfo() = RouteInfo(
        latitude = point.lat, longitude = point.lon, poiName = name, poiId = "0", address = name, poiSubId = "0",
    )

    private fun Stop.toWaypoint(index: WaypointIndex) = RequestWaypointInfo(
        latitude = point.lat, longitude = point.lon, poiName = name, waypointIndex = index,
        poiId = "0", address = name, poiSubId = "0",
    )

    private companion object {
        const val TAG = "DRIVE"
        const val WAYPOINT_ADDED = "ADD"
        val INDEXES = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
    }
}
