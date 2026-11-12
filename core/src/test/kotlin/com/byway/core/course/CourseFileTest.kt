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
        assertThrows(IllegalArgumentException::class.java) { parseCourse(json(version = 3)) }
    }

    @Test
    fun rejectsSectionsThatLeaveAGap() {
        assertThrows(IllegalArgumentException::class.java) { parseCourse(json(secondStart = 3)) }
    }

    private fun v2(attribution: String = "OSM", spotIndex: Int = 1) = """
        {"schemaVersion":2,"id":"t","name":"시험","attribution":"$attribution","summary":"강을 따라가는 길",
         "toleranceMeters":800.0,
         "tags":{"themes":["river"],"moods":["alone"],"times":["sunset"],"seasons":["spring"],"ages":["20s"]},
         "spots":[{"name":"전망대","kind":"view","lat":37.0,"lon":127.1,"pathIndex":$spotIndex}],
         "path":[[37.0,127.0],[37.0,127.1]],
         "sections":[{"name":"앞","startIndex":0,"endIndex":1}]}
    """

    @Test
    fun v2ReadsSpots() {
        assertEquals(Spot("전망대", "view", GeoPoint(37.0, 127.1), 1), parseCourse(v2()).spots.single())
    }

    @Test
    fun v2ReadsTags() {
        assertEquals(
            CourseTags(listOf("river"), listOf("alone"), listOf("sunset"), listOf("spring"), listOf("20s")),
            parseCourse(v2()).tags,
        )
    }

    @Test
    fun theFullSceneryCreditIsOptional() {
        assertEquals("", parseCourse(v2()).sceneryDetail)
        val withDetail = v2().replace(""""summary":""", """"sceneryDetail":"전문","summary":""")
        assertEquals("전문", parseCourse(withDetail).sceneryDetail)
    }

    @Test
    fun theSceneryCreditIsOptional() {
        assertEquals("", parseCourse(v2()).sceneryCredit)
        val withPhoto = v2().replace(""""summary":""", """"sceneryCredit":"작가 (CC0)","summary":""")
        assertEquals("작가 (CC0)", parseCourse(withPhoto).sceneryCredit)
    }

    @Test
    fun v2ReadsSummaryAttributionAndTolerance() {
        val course = parseCourse(v2())
        assertEquals("강을 따라가는 길", course.summary)
        assertEquals("OSM", course.attribution)
        assertEquals(800.0, course.toleranceMeters)
    }

    @Test
    fun readsRegionAndElevationWhenPresent() {
        val text = v2().replace("\"spots\":", "\"region\":\"강원\",\"elevation\":[10.0,20.5],\"spots\":")
        val course = parseCourse(text)
        assertEquals("강원", course.region)
        assertEquals(listOf(10.0, 20.5), course.elevation)
    }

    @Test
    fun v1FilesStillReadWithoutSpots() {
        assertEquals(emptyList<Spot>(), parseCourse(json()).spots)
    }

    @Test
    fun rejectsASpotOutsideThePath() {
        assertThrows(IllegalArgumentException::class.java) { parseCourse(v2(spotIndex = 9)) }
    }

    @Test
    fun rejectsV2WithoutAttribution() {
        assertThrows(IllegalArgumentException::class.java) { parseCourse(v2(attribution = "")) }
    }

    private val allCourses by lazy {
        File("../app/src/main/assets/courses").listFiles().orEmpty().map { parseCourse(it.readText()) }
    }

    @Test
    fun everyCourseSaysWhenAndForWhomItIsPicked() {
        allCourses.forEach { course ->
            assertTrue("${course.id} 계절", course.tags.seasons.isNotEmpty())
            assertTrue("${course.id} 나이대", course.tags.ages.isNotEmpty())
        }
    }

    @Test
    fun theDemoCoursesStartNearPangyo() {
        val pangyo = GeoPoint(37.3947, 127.1112)
        assertTrue(allCourses.count { it.path.first().distanceTo(pangyo) < 2_000.0 } >= 4)
    }

    @Test
    fun everyCourseCanBeDrivenToTheEndWithoutStraying() {
        allCourses.forEach { course ->
            val engine = CourseEngine()
            engine.onEvent(DriveEvent.CourseStarted(course))
            val commands = course.path.flatMap { engine.onEvent(DriveEvent.LocationUpdated(it)) }
            assertTrue(course.id, DriveCommand.Publish(DriveEvent.CourseFinished) in commands)
            assertTrue(course.id, commands.filterIsInstance<DriveCommand.Say>().none { "벗어났습니다" in it.text })
        }
    }

    @Test
    fun writtenCourseReadsBack() {
        assertEquals(15, allCourses.size)
        allCourses.forEach { course -> assertEquals(course.id, course, parseCourse(writeCourse(course))) }
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
