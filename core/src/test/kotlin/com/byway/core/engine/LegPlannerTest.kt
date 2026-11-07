package com.byway.core.engine

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.course.Stop
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegPlannerTest {
    private fun stops(count: Int) = (1..count).map { Stop("지점$it", GeoPoint(37.0, 127.0 + it * 0.01), it * 10) }

    @Test
    fun threeStopsFitInOneLeg() {
        val legs = planLegs(stops(3))
        assertEquals(1, legs.size)
        assertEquals(listOf("지점1", "지점2"), legs[0].waypoints.map { it.name })
        assertEquals("지점3", legs[0].destination.name)
    }

    @Test
    fun fourthStopStartsANewLeg() {
        val legs = planLegs(stops(4))
        assertEquals(listOf(2, 0), legs.map { it.waypoints.size })
        assertEquals("지점4", legs[1].destination.name)
    }

    @Test
    fun noLegEverCarriesMoreThanTwoWaypoints() {
        assertTrue(planLegs(stops(20)).all { it.waypoints.size <= 2 })
    }

    @Test
    fun everyStopAppearsOnceInOrder() {
        val names = planLegs(stops(8)).flatMap { it.waypoints + it.destination }.map { it.name }
        assertEquals(stops(8).map { it.name }, names)
    }

    @Test
    fun aRoomierFollowerCarriesMoreWaypointsPerLeg() {
        assertEquals(listOf(3, 3), planLegs(stops(8), maxWaypoints = 3).map { it.waypoints.size })
    }

    private val ridgeCourse = Course(
        id = "ridge",
        name = "능선",
        path = (0..10).map {
            GeoPoint(37.0 + 0.05 * (5 - kotlin.math.abs(it - 5)) / 5, 127.0 + it * 0.01)
        },
        sections = listOf(Section("오르막", 0, 5), Section("내리막", 6, 10)),
    )

    @Test
    fun stopsSkipTheStartingPoint() {
        assertEquals(listOf(5, 10), planStops(ridgeCourse, 100.0).map { it.pathIndex })
    }

    @Test
    fun stopsAreNamedAfterTheirSection() {
        assertEquals(listOf("오르막", "내리막"), planStops(ridgeCourse, 100.0).map { it.name })
    }
}
