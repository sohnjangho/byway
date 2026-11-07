package com.byway.core.engine

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.course.Stop
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveCommand.Publish
import com.byway.core.drive.DriveCommand.Say
import com.byway.core.drive.DriveCommand.ShowProgress
import com.byway.core.drive.DriveCommand.FollowRoute
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.RouteAbility
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseEngineTest {
    /** 동쪽으로 곧게 가는 약 8.8km 길. 지점 간격 약 88m. */
    private val path = (0..100).map { GeoPoint(37.0, 127.0 + it * 0.001) }
    private val sections = listOf(Section("첫 구간", 0, 49), Section("강변", 50, 100, mood = "river"))
    private val course = Course("c", "시험", path, sections)
    private val stops = listOf(20, 40, 60, 80, 100).map { Stop("지점$it", path[it], it) }
    private fun engine() = CourseEngine(DriveTuning(simplifyToleranceMeters = 50.0, offCourseMeters = 150.0, lookaheadMeters = 2_000.0), stopsOf = { stops })
    private fun started() = engine().also { it.onEvent(DriveEvent.CourseStarted(course)) }

    private fun CourseEngine.at(index: Int) = onEvent(DriveEvent.LocationUpdated(path[index]))
    private fun CourseEngine.drive(range: IntProgression) = range.flatMap { at(it) }
    private fun CourseEngine.offBy(index: Int, degrees: Double = 0.01) =
        onEvent(DriveEvent.LocationUpdated(GeoPoint(37.0 + degrees, path[index].lon)))
    private fun List<DriveCommand>.routes() = filterIsInstance<FollowRoute>().map { it.leg }
    private fun List<DriveCommand>.says() = filterIsInstance<Say>().map { it.text }

    @Test
    fun startingPutsTheFirstLegOnTheNavigation() {
        val commands = engine().onEvent(DriveEvent.CourseStarted(course))
        val leg = commands.routes().single()
        assertEquals(listOf(20, 40), leg.waypoints.map { it.pathIndex })
        assertEquals(60, leg.destination.pathIndex)
        assertEquals(listOf("시험 코스를 시작합니다."), commands.says())
    }

    @Test
    fun drivingInsideALegOnlyUpdatesTheProgress() {
        val progress = started().at(10).single() as ShowProgress
        assertEquals(0, progress.sectionIndex)
        assertEquals(8_000.0, progress.remainingMeters, 50.0)
    }

    @Test
    fun theProgressFollowsTheCourseIntoTheNextSection() {
        val last = started().drive(1..55).filterIsInstance<ShowProgress>().last()
        assertEquals(1, last.sectionIndex)
    }

    @Test
    fun passingTheLegDestinationPutsTheNextLeg() {
        val commands = started().drive(1..61)
        val next = commands.routes().last()
        assertEquals(80, next.waypoints.single().pathIndex)
        assertEquals(100, next.destination.pathIndex)
        assertTrue("다음 구간으로 이어갑니다." in commands.says())
    }

    @Test
    fun enteringASectionPublishesItOnce() {
        val published = started().drive(1..55).filterIsInstance<Publish>().map { it.event }
        assertEquals(listOf(DriveEvent.SectionEntered(sections[1])), published)
    }

    @Test
    fun twoStrayFixesDoNothing() {
        val engine = started().also { it.drive(1..10) }
        val commands = engine.offBy(11) + engine.offBy(12)
        assertTrue(commands.routes().isEmpty())
    }

    @Test
    fun oneGoodFixResetsTheStrayCount() {
        val engine = started().also { it.drive(1..10) }
        engine.offBy(11); engine.offBy(12); engine.at(13)
        val commands = engine.offBy(14) + engine.offBy(15)
        assertTrue(commands.routes().isEmpty())
    }

    @Test
    fun thirdStrayFixRoutesBackToTheNearestCoursePointAhead() {
        val engine = started().also { it.drive(1..10) }
        engine.offBy(30); engine.offBy(30)
        val commands = engine.offBy(30)
        val leg = commands.routes().single()
        assertTrue(leg.waypoints.isEmpty())
        assertEquals(30, leg.destination.pathIndex)
        assertEquals(listOf("코스를 벗어났습니다. 코스로 돌아가는 길을 안내합니다."), commands.says())
    }

    @Test
    fun strayingFurtherDoesNotRepeatTheGuidance() {
        val engine = started().also { it.drive(1..10) }
        repeat(3) { engine.offBy(30) }
        assertTrue(engine.offBy(31).routes().isEmpty())
    }

    @Test
    fun returningPutsTheRestOfTheLegBack() {
        val engine = started().also { it.drive(1..10) }
        repeat(3) { engine.offBy(30) }
        val commands = engine.at(30)
        val leg = commands.routes().single()
        assertEquals(listOf(40), leg.waypoints.map { it.pathIndex })
        assertEquals(60, leg.destination.pathIndex)
        assertTrue("코스로 돌아왔습니다." in commands.says())
    }

    @Test
    fun returningPastTheLegMovesToTheNextLeg() {
        val engine = started().also { it.drive(1..10) }
        repeat(3) { engine.offBy(70) }
        val leg = engine.at(70).routes().single()
        assertEquals(100, leg.destination.pathIndex)
        assertEquals(listOf(80), leg.waypoints.map { it.pathIndex })
    }

    @Test
    fun reachingTheEndFinishesOnce() {
        val engine = started()
        val commands = engine.drive(1..100)
        assertTrue(Publish(DriveEvent.CourseFinished) in commands)
        assertTrue("코스를 끝까지 달렸습니다." in commands.says())
        assertTrue(engine.at(100).isEmpty())
    }

    @Test
    fun aRoadUsedTwiceDoesNotJumpAhead() {
        val out = (0..50).map { GeoPoint(37.0, 127.0 + it * 0.001) }
        val back = (49 downTo 0).map { GeoPoint(37.0, 127.0 + it * 0.001) }
        val loop = out + back
        val loopCourse = Course("l", "왕복", loop, listOf(Section("왕복", 0, loop.lastIndex)))
        val end = Stop("끝", loop.last(), loop.lastIndex)
        val engine = CourseEngine(DriveTuning(lookaheadMeters = 1_000.0), stopsOf = { listOf(end) })
        engine.onEvent(DriveEvent.CourseStarted(loopCourse))
        val commands = engine.onEvent(DriveEvent.LocationUpdated(loop[1]))
        assertFalse(Publish(DriveEvent.CourseFinished) in commands)
    }

    @Test
    fun aCourseCanSetItsOwnTolerance() {
        val ridge = (0..10).map { GeoPoint(37.0 + 0.005 * (5 - kotlin.math.abs(it - 5)) / 5, 127.0 + it * 0.01) }
        fun firstLegStops(tolerance: Double?): Int {
            val own = Course("r", "능선", ridge, listOf(Section("능선", 0, 10)), toleranceMeters = tolerance)
            val leg = CourseEngine().onEvent(DriveEvent.CourseStarted(own)).routes().single()
            return leg.waypoints.size + 1
        }
        assertEquals(1, firstLegStops(null))
        assertEquals(2, firstLegStops(100.0))
    }

    @Test
    fun aWideCourseToleranceWidensTheStrayLimit() {
        val wide = Course("w", "넓음", path, sections, toleranceMeters = 2_000.0)
        val engine = CourseEngine(DriveTuning(offCourseMeters = 1_300.0), stopsOf = { stops })
        engine.onEvent(DriveEvent.CourseStarted(wide))
        val commands = (1..3).flatMap { engine.offBy(10 + it, degrees = 0.015) }
        assertTrue(commands.routes().isEmpty())
    }

    @Test
    fun beyondTheCourseToleranceStillCountsAsStraying() {
        val wide = Course("w", "넓음", path, sections, toleranceMeters = 2_000.0)
        val engine = CourseEngine(DriveTuning(offCourseMeters = 1_300.0, lookaheadMeters = 2_000.0), stopsOf = { stops })
        engine.onEvent(DriveEvent.CourseStarted(wide))
        val commands = (1..3).flatMap { engine.offBy(10 + it, degrees = 0.03) }
        assertEquals(1, commands.routes().size)
    }

    @Test
    fun aFollowerWithMoreWaypointsGetsLongerLegs() {
        val roomy = CourseEngine(DriveTuning(), RouteAbility(maxWaypoints = 3), { stops })
        val leg = roomy.onEvent(DriveEvent.CourseStarted(course)).routes().single()
        assertEquals(listOf(20, 40, 60), leg.waypoints.map { it.pathIndex })
        assertEquals(80, leg.destination.pathIndex)
    }

    @Test
    fun aFollowerThatTakesTheWholePathGetsItOnce() {
        val whole = CourseEngine(DriveTuning(), RouteAbility(acceptsWholePath = true), { stops })
        val first = whole.onEvent(DriveEvent.CourseStarted(course)).filterIsInstance<FollowRoute>().single()
        assertEquals(path, first.path)
        assertEquals(100, first.leg.destination.pathIndex)
    }

    @Test
    fun aWholePathNeedsNoLegChanges() {
        val whole = CourseEngine(DriveTuning(), RouteAbility(acceptsWholePath = true), { stops })
        whole.onEvent(DriveEvent.CourseStarted(course))
        val commands = (1..100).flatMap { whole.at(it) }
        assertTrue(commands.routes().isEmpty())
        assertTrue(Publish(DriveEvent.CourseFinished) in commands)
    }

    @Test
    fun theNavigationGetsWaypointsNotThePath() {
        val first = engine().onEvent(DriveEvent.CourseStarted(course)).filterIsInstance<FollowRoute>().single()
        assertEquals(null, first.path)
    }

    @Test
    fun locationBeforeStartIsIgnored() {
        assertTrue(engine().at(5).isEmpty())
    }
}
