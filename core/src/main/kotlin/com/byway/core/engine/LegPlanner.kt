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

/** 경유지 [maxWaypoints]개와 목적지 1개씩 묶는다. */
fun planLegs(stops: List<Stop>, maxWaypoints: Int = 2): List<Leg> =
    stops.chunked(maxWaypoints + 1).map { Leg(waypoints = it.dropLast(1), destination = it.last()) }
