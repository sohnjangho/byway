package com.byway.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.byway.core.course.Leg
import com.byway.core.course.Stop
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.RouteAbility
import com.byway.core.drive.RouteFollower

/** 차의 내장 내비에 구간을 올린다. */
class PleosNavi(context: Context) : RouteFollower {
    override val ability = RouteAbility()
    private val navi = NaviHelper(context)
    private var connected = false
    private var waiting: Leg? = null

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { Log.i(TAG, "route started $routeStartInfo") }
        override fun onError(errorCode: NaviErrorCode) { Log.w(TAG, "navi error $errorCode") }
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
        Log.i(TAG, "show leg via=${leg.waypoints.size} to=${leg.destination.pathIndex}")
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

    fun release() {
        if (connected) {
            navi.cancelRoute()
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
        val INDEXES = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
    }
}
