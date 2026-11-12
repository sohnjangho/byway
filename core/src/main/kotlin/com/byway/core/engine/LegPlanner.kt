package com.byway.core.engine

import com.byway.core.course.Course
import com.byway.core.course.Leg
import com.byway.core.course.Stop
import com.byway.core.geo.keyPointIndexes

/** 코스를 내비에 올릴 지점 열로 바꾼다. 출발점은 현재 위치이므로 뺀다. */
fun planStops(course: Course, toleranceMeters: Double): List<Stop> =
    keyPointIndexes(course.path, toleranceMeters).drop(1).map { index ->
        val section = course.sections.first { index in it.startIndex..it.endIndex }
        Stop(section.name, course.path[index], index)
    }

/**
 * 코스를 달리는 동안 내비에 경로를 올리는 횟수. 엔진은 목적지에 닿기 전에 다음 경로를 올리면서
 * 앞 경로의 목적지를 첫 경유지로 다시 싣는다. 그래서 처음 한 번 뒤로는 [maxWaypoints]개씩 나아간다.
 * 지점 간격이 [DriveTuning.legSwapAheadMeters]보다 넓을 때의 값이다. 더 촘촘하면 엔진이 더 자주 올린다.
 */
fun routeUploads(stopCount: Int, maxWaypoints: Int = 2): Int {
    val first = maxWaypoints + 1
    return if (stopCount <= first) 1 else 1 + (stopCount - first + maxWaypoints - 1) / maxWaypoints
}

/** 경유지 [maxWaypoints]개와 목적지 1개씩 묶는다. 엔진이 처음 올리는 경로가 첫 묶음과 같다. */
fun planLegs(stops: List<Stop>, maxWaypoints: Int = 2): List<Leg> =
    stops.chunked(maxWaypoints + 1).map { Leg(waypoints = it.dropLast(1), destination = it.last()) }
