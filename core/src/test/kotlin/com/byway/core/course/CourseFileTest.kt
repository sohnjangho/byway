package com.byway.core.course

import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.engine.CourseEngine
import com.byway.core.engine.DriveTuning
import com.byway.core.engine.planLegs
import com.byway.core.engine.planStops
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CourseFileTest {
    private fun json(version: Int = 1, secondStart: Int = 2) = """
        {"schemaVersion":$version,"id":"t","name":"시험","attribution":"OSM",
         "path":[[37.0,127.0],[37.0,127.1],[37.0,127.2],[37.0,127.3]],
         "sections":[{"name":"앞","startIndex":0,"endIndex":1,"mood":"river"},
                     {"name":"뒤","startIndex":$secondStart,"endIndex":3}]}
    """

    @Test
    fun readsPathAndSections() {
        val course = parseCourse(json())
        assertEquals(GeoPoint(37.0, 127.1), course.path[1])
        assertEquals(Section("앞", 0, 1, "river"), course.sections[0])
        assertEquals(null, course.sections[1].mood)
    }

    @Test
    fun rejectsAnUnknownSchemaVersion() {
        assertThrows(IllegalArgumentException::class.java) { parseCourse(json(version = 2)) }
    }

    @Test
    fun rejectsSectionsThatLeaveAGap() {
        assertThrows(IllegalArgumentException::class.java) { parseCourse(json(secondStart = 3)) }
    }

    private val demoCourse by lazy {
        parseCourse(File("../app/src/main/assets/courses/paldang-sunset.json").readText())
    }

    @Test
    fun demoCourseSplitsIntoLegalLegs() {
        val legs = planLegs(planStops(demoCourse, DriveTuning().simplifyToleranceMeters))
        assertTrue(legs.size > 1)
        assertTrue(legs.all { it.waypoints.size <= 2 })
    }

    @Test
    fun drivingTheWholeDemoCourseFinishes() {
        val engine = CourseEngine()
        engine.onEvent(DriveEvent.CourseStarted(demoCourse))
        val commands = demoCourse.path.flatMap { engine.onEvent(DriveEvent.LocationUpdated(it)) }
        assertTrue(DriveCommand.Publish(DriveEvent.CourseFinished) in commands)
    }

    @Test
    fun drivingTheDemoCourseNeverLooksLikeStraying() {
        val engine = CourseEngine()
        engine.onEvent(DriveEvent.CourseStarted(demoCourse))
        val says = demoCourse.path.flatMap { engine.onEvent(DriveEvent.LocationUpdated(it)) }
            .filterIsInstance<DriveCommand.Say>().map { it.text }
        assertTrue(says.none { "벗어났습니다" in it })
    }

    @Test
    fun demoCourseVisitsEverySectionInOrder() {
        val engine = CourseEngine()
        val first = engine.onEvent(DriveEvent.CourseStarted(demoCourse))
        val rest = demoCourse.path.flatMap { engine.onEvent(DriveEvent.LocationUpdated(it)) }
        val entered = (first + rest).filterIsInstance<DriveCommand.Publish>()
            .map { it.event }.filterIsInstance<DriveEvent.SectionEntered>().map { it.section.name }
        assertEquals(demoCourse.sections.map { it.name }, entered)
    }
}
