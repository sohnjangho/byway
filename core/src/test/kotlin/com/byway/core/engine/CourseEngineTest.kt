package com.byway.core.engine

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.course.Stop
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveCommand.Ask
import com.byway.core.drive.DriveCommand.FollowRoute
import com.byway.core.drive.DriveCommand.Publish
import com.byway.core.drive.DriveCommand.Say
import com.byway.core.drive.DriveCommand.ShowProgress
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.Prompt
import com.byway.core.drive.RouteAbility
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseEngineTest {
    /** 동쪽으로 곧게 가는 약 8.8km 길. 지점 간격 약 88m. */
    private val path = (0..100).map { GeoPoint(37.0, 127.0 + it * 0.001) }
    private val sections = listOf(Section("첫 구간", 0, 49), Section("강변", 50, 100, mood = "river"))
    private val course = Course("c", "시험", path, sections)
    private val stops = listOf(20, 40, 60, 80, 100).map { Stop("지점$it", path[it], it) }

    /** 이탈 기준 150m, 복귀 기준 50m, 탐색 2km(약 22칸), 다음 경로는 1km(약 11칸) 전에 잇는다. */
    private val tuning = DriveTuning(
        simplifyToleranceMeters = 50.0,
        offCourseMeters = 150.0,
        lookaheadMeters = 2_000.0,
        askWaitFixes = 4,
        rejoinMeters = 50.0,
        settleFixes = 6,
    )
    private fun engine(stops: List<Stop> = this.stops) = CourseEngine(tuning, stopsOf = { stops })
    private fun started(course: Course = this.course) = engine().also { it.onEvent(DriveEvent.CourseStarted(course)) }

    /** 10번 지점까지 달린 뒤 세 번 벗어나 질문을 받은 상태. */
    private fun asked() = started().also { engine ->
        engine.drive(1..10)
        repeat(3) { engine.offBy(11 + it) }
    }

    /** 질문에 아니라고 답해 복귀 경로(20번 지점부터)가 올라간 상태. */
    private fun rejoining() = asked().also { it.answer(false) }

    private fun CourseEngine.at(index: Int) = onEvent(DriveEvent.LocationUpdated(path[index]))
    private fun CourseEngine.drive(range: IntProgression) = range.flatMap { at(it) }

    /** 코스에서 북쪽으로 벗어난 자리. 0.01도는 약 1.1km, 0.001도는 약 110m 다. */
    private fun CourseEngine.offBy(index: Int, degrees: Double = 0.01) =
        onEvent(DriveEvent.LocationUpdated(GeoPoint(37.0 + degrees, path[index].lon)))
    private fun CourseEngine.answer(yes: Boolean) = onEvent(DriveEvent.Answered(yes))
    private fun CourseEngine.park(parked: Boolean = true) = onEvent(DriveEvent.DrivingStateChanged(parked))
    private fun List<DriveCommand>.routes() = filterIsInstance<FollowRoute>().map { it.leg }
    private fun List<DriveCommand>.says() = filterIsInstance<Say>().map { it.text }
    private fun List<DriveCommand>.asks() = filterIsInstance<Ask>().map { it.prompt }
    private fun com.byway.core.course.Leg.indexes() = waypoints.map { it.pathIndex } + destination.pathIndex

    // 출발

    @Test
    fun startingPutsTheFirstLegOnTheNavigation() {
        val commands = engine().onEvent(DriveEvent.CourseStarted(course))
        assertEquals(listOf(20, 40, 60), commands.routes().single().indexes())
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
    fun enteringASectionPublishesItOnce() {
        val published = started().drive(1..55).filterIsInstance<Publish>().map { it.event }
        assertEquals(listOf(DriveEvent.SectionEntered(sections[1])), published)
    }

    @Test
    fun locationBeforeStartIsIgnored() {
        assertTrue(engine().at(5).isEmpty())
    }

    // 접근과 중간 합류 (P4)

    @Test
    fun startingFarFromTheCourseRoutesToTheStartFirst() {
        val commands = started().offBy(0, degrees = 0.2)
        assertEquals(listOf(0, 20, 40), commands.routes().single().indexes())
        assertEquals(listOf("코스 출발점으로 안내합니다."), commands.says())
    }

    /** 집에서 출발하는 운전자에게 이탈을 알리거나 들르는지 묻지 않는다. */
    @Test
    fun approachingTheCourseIsNotStraying() {
        val engine = started()
        engine.offBy(0, degrees = 0.2)
        assertTrue((1..10).flatMap { engine.offBy(0, degrees = 0.2 - it * 0.01) }.isEmpty())
    }

    @Test
    fun arrivingAtTheStartAfterApproachingNeedsNoNewRoute() {
        val engine = started()
        engine.offBy(0, degrees = 0.2)
        assertTrue(engine.drive(0..5).routes().isEmpty())
    }

    @Test
    fun startingMidCourseJoinsThere() {
        val commands = started().at(50)
        assertEquals(listOf(60, 80, 100), commands.routes().single().indexes())
        assertTrue("코스 중간에서 시작합니다." in commands.says())
        assertTrue(Publish(DriveEvent.SectionEntered(sections[1])) in commands)
    }

    /** 접근하는 길이 코스 중간을 가로질러도 거기서 합류한 것으로 보지 않는다. 처음 위치에서만 정한다. */
    @Test
    fun crossingTheCourseWhileApproachingIsNotJoining() {
        val engine = started()
        engine.offBy(0, degrees = 0.2)
        assertTrue(engine.at(50).isEmpty())
    }

    private val outAndBack = ((0..50) + (49 downTo 0)).map { GeoPoint(37.0, 127.0 + it * 0.001) }
    private val roundTrip = Course("l", "왕복", outAndBack, listOf(Section("왕복", 0, outAndBack.lastIndex)))
    private fun roundTripEngine() = CourseEngine(DriveTuning(lookaheadMeters = 1_000.0), stopsOf = { listOf(Stop("끝", outAndBack.last(), outAndBack.lastIndex)) })
        .also { it.onEvent(DriveEvent.CourseStarted(roundTrip)) }

    @Test
    fun aRoadUsedTwiceDoesNotJumpAhead() {
        val commands = roundTripEngine().onEvent(DriveEvent.LocationUpdated(outAndBack[1]))
        assertFalse(Publish(DriveEvent.CourseFinished) in commands)
    }

    /** 가는 길 중간에서 합류하면 오는 길의 같은 자리로 건너뛰지 않는다. */
    @Test
    fun joiningARoundTripMidwayStaysOnTheWayOut() {
        val progress = roundTripEngine().onEvent(DriveEvent.LocationUpdated(outAndBack[30])).filterIsInstance<ShowProgress>().single()
        assertEquals(70 * 88.8, progress.remainingMeters, 100.0)
    }

    // 시작할 때 차의 자리를 아는 경우: 경로를 한 번만 올린다

    private val farAway = GeoPoint(37.2, 127.0)

    /** 경로를 잇달아 두 번 올리면 내비가 앞 경로를 지우는 이벤트가 운전자 조작처럼 보인다. */
    @Test
    fun knowingTheCarIsFarOffPutsUpOneRouteToTheStart() {
        val engine = engine()
        val commands = engine.onEvent(DriveEvent.CourseStarted(course, at = farAway))
        assertEquals(listOf(0, 20, 40), commands.routes().single().indexes())
        assertTrue("코스 출발점으로 안내합니다." in commands.says())
        assertTrue(engine.onEvent(DriveEvent.LocationUpdated(farAway)).routes().isEmpty())
    }

    @Test
    fun knowingTheCarIsAtTheStartNeedsNoSecondRoute() {
        val engine = engine()
        assertEquals(listOf(20, 40, 60), engine.onEvent(DriveEvent.CourseStarted(course, at = path[2])).routes().single().indexes())
        assertTrue(engine.at(3).routes().isEmpty())
    }

    @Test
    fun knowingTheCarIsMidCourseJoinsThereOnce() {
        val engine = engine()
        val commands = engine.onEvent(DriveEvent.CourseStarted(course, at = path[50]))
        assertEquals(listOf(60, 80, 100), commands.routes().single().indexes())
        assertTrue(Publish(DriveEvent.SectionEntered(sections[1])) in commands)
        assertTrue(engine.at(51).routes().isEmpty())
    }

    /** 저장한 뒤 더 달렸으면 지나온 경유지를 다시 올리지 않는다. 유턴을 시키게 된다. */
    @Test
    fun resumingAheadOfTheSavedPointLeavesPassedStopsOut() {
        val commands = engine().onEvent(DriveEvent.CourseStarted(course, resumeFrom = 10, at = path[30]))
        assertEquals(listOf(40, 60, 80), commands.routes().single().indexes())
    }

    // 코스에 닿기 전에는 연출을 켜지 않는다

    private fun List<DriveCommand>.sections() = filterIsInstance<Publish>().map { it.event }.filterIsInstance<DriveEvent.SectionEntered>().map { it.section }

    /** 부산에서 출발하는 운전자의 고속도로에서 창문이 열리면 안 된다. */
    @Test
    fun theCabinSceneWaitsWhileApproachingTheCourse() {
        assertTrue(engine().onEvent(DriveEvent.CourseStarted(course, at = farAway)).sections().isEmpty())
    }

    @Test
    fun reachingTheStartAnnouncesTheFirstSectionOnce() {
        val engine = engine().also { it.onEvent(DriveEvent.CourseStarted(course, at = farAway)) }
        assertEquals(listOf(sections[0]), (engine.at(0) + engine.at(1) + engine.at(2)).sections())
    }

    @Test
    fun startingOnTheCourseAnnouncesAtOnce() {
        assertEquals(listOf(sections[0]), engine().onEvent(DriveEvent.CourseStarted(course, at = path[2])).sections())
    }

    @Test
    fun joiningFarAlongTheCourseAnnouncesThatSectionOnce() {
        val engine = engine().also { it.onEvent(DriveEvent.CourseStarted(course, at = farAway)) }
        assertEquals(listOf(sections[1]), (engine.at(50) + engine.at(54) + engine.at(55)).sections())
    }

    /** 멈췄다 코스 밖에서 이어가면 코스로 돌아올 때까지 연출을 다시 켜지 않는다. */
    @Test
    fun resumingAwayFromTheCourseWaitsUntilBackOnIt() {
        val engine = started().also { it.drive(1..10) }
        engine.onEvent(DriveEvent.RouteTakenOver)
        engine.offBy(12, degrees = 0.05)
        assertTrue(engine.answer(true).sections().isEmpty())
        assertEquals(listOf(sections[0]), (engine.at(14) + engine.at(15) + engine.at(16)).sections())
    }

    // 접근 중 합류

    /** 출발점 안내를 무시하고 코스 먼 곳으로 들어와 코스를 따라 달리면 거기서 시작한 것이다. */
    @Test
    fun anApproachThatFollowsTheCourseFarAlongStartsThere() {
        val engine = started()
        engine.offBy(0, degrees = 0.2)
        assertTrue(engine.at(50).isEmpty())
        val commands = engine.at(54)
        assertEquals(listOf(60, 80, 100), commands.routes().single().indexes())
        assertTrue("코스 중간에서 시작합니다." in commands.says())
    }

    // 이어가기 (P2)

    @Test
    fun resumingStartsFromTheSavedPoint() {
        val commands = engine().onEvent(DriveEvent.CourseStarted(course, resumeFrom = 45))
        assertEquals(listOf(60, 80, 100), commands.routes().single().indexes())
        assertEquals(listOf("시험 코스를 이어갑니다."), commands.says())
        assertTrue(Publish(DriveEvent.SectionEntered(sections[0])) in commands)
    }

    @Test
    fun aSnapshotBringsBackTheSameLeg() {
        val first = started()
        val before = first.drive(1..50).routes().last()
        val snapshot = requireNotNull(first.snapshot())
        assertEquals("c", snapshot.courseId)
        val second = engine()
        val after = second.onEvent(DriveEvent.CourseStarted(course, snapshot.progress)).routes().single()
        assertEquals(before, after)
        assertTrue(second.at(51).routes().isEmpty())
    }

    /** 저장한 지점보다 한참 더 달린 뒤 꺼졌어도 지금 자리부터 올린다. */
    @Test
    fun resumingFurtherAlongJoinsWhereTheCarIs() {
        val engine = engine().also { it.onEvent(DriveEvent.CourseStarted(course, resumeFrom = 10)) }
        assertEquals(listOf(80, 100), engine.at(70).routes().single().indexes())
    }

    @Test
    fun thereIsNoSnapshotWithoutARun() {
        assertNull(engine().snapshot())
        assertNull(started().also { it.drive(1..100) }.snapshot())
    }

    // 다음 경로 잇기 (P3)

    @Test
    fun theNextLegGoesUpOneKilometreBeforeTheDestination() {
        val engine = started()
        assertTrue(engine.drive(1..48).routes().isEmpty())
        val commands = engine.at(49)
        assertEquals(listOf(60, 80, 100), commands.routes().single().indexes())
        assertTrue("다음 구간으로 이어갑니다." in commands.says())
    }

    /** 교체하면 안내가 몇 초 끊긴다. 45번 지점에서 받은 안내 지점은 약 46.1번과 49.5번 자리다. */
    @Test
    fun aTurnJustAheadHoldsTheSwapUntilItIsBehind() {
        val engine = started()
        engine.drive(1..45)
        engine.onEvent(DriveEvent.RouteTurns(listOf(100.0, 300.0)))
        assertTrue(engine.drive(46..49).routes().isEmpty())
        assertEquals(listOf(60, 80, 100), engine.at(50).routes().single().indexes())
    }

    @Test
    fun aFarTurnDoesNotHoldTheSwap() {
        val engine = started()
        engine.drive(1..45)
        engine.onEvent(DriveEvent.RouteTurns(listOf(1_200.0)))
        assertTrue(engine.drive(46..48).routes().isEmpty())
        assertEquals(listOf(60, 80, 100), engine.at(49).routes().single().indexes())
    }

    /** 회전이 이어져도 목적지 400m 안에서는 올린다. 내비가 "목적지 도착"으로 안내를 끝내면 안 된다. */
    @Test
    fun turnsCannotHoldTheSwapPastTheLastMoment() {
        val engine = started()
        engine.drive(1..45)
        engine.onEvent(DriveEvent.RouteTurns(List(40) { 100.0 }))
        assertTrue(engine.drive(46..55).routes().isEmpty())
        assertEquals(listOf(60, 80, 100), engine.at(56).routes().single().indexes())
    }

    /** 교체 뒤에는 앞 경로의 안내 지점이 남아 있으면 안 된다. 약 70번 자리의 지점은 앞 경로의 것이다. */
    @Test
    fun aNewRouteForgetsTheTurnsOfTheOldOne() {
        val engine = engine(listOf(16, 32, 48, 64, 80, 100).map { Stop("지점$it", path[it], it) })
        engine.onEvent(DriveEvent.CourseStarted(course))
        engine.drive(1..30)
        engine.onEvent(DriveEvent.RouteTurns(listOf(3_550.0)))
        assertEquals(listOf(48, 64, 80), engine.drive(31..37).routes().single().indexes())
        assertTrue(engine.drive(38..68).routes().isEmpty())
        assertEquals(listOf(80, 100), engine.at(69).routes().single().indexes())
    }

    @Test
    fun theLastLegIsNotSwapped() {
        assertEquals(1, started().drive(1..99).routes().size)
    }

    /**
     * 화면의 "몇 번에 나눠 안내"는 엔진이 실제로 경로를 올리는 횟수와 같아야 한다.
     * 지점 간격이 교체 거리(1km)보다 넓은 코스에서 그렇다. 실린 코스는 간격이 2.1km 이상이다.
     */
    @Test
    fun theNumberOfRoutesMatchesWhatTheScreenPromises() {
        val six = listOf(16, 32, 48, 64, 80, 100).map { Stop("지점$it", path[it], it) }
        listOf(stops, six, stops.takeLast(1)).forEach { planned ->
            val engine = engine(planned)
            val routes = (engine.onEvent(DriveEvent.CourseStarted(course)) + engine.drive(1..100)).routes()
            assertEquals(routeUploads(planned.size), routes.size)
        }
    }

    @Test
    fun noLegCarriesMoreThanTwoWaypoints() {
        val dense = (1..25).map { Stop("지점", path[it * 4], it * 4) }
        val engine = engine(dense)
        val routes = (engine.onEvent(DriveEvent.CourseStarted(course)) + engine.drive(1..100)).routes()
        assertTrue(routes.all { it.waypoints.size <= 2 })
    }

    /** 지점이 촘촘해도 앞 경로와 이어 붙인 지점을 지나기 전에는 다시 올리지 않는다. */
    @Test
    fun aDenseCourseDoesNotSwapAgainBeforeTheSharedStop() {
        val dense = (1..25).map { Stop("지점", path[it * 4], it * 4) }
        val engine = engine(dense).also { it.onEvent(DriveEvent.CourseStarted(course)) }
        assertEquals(listOf(listOf(8, 12, 16)), engine.drive(1..11).routes().map { it.indexes() })
    }

    // 벗어나면 먼저 묻는다 (P5)

    @Test
    fun twoStrayFixesDoNothing() {
        val engine = started().also { it.drive(1..10) }
        assertTrue((engine.offBy(11) + engine.offBy(12)).isEmpty())
    }

    @Test
    fun oneGoodFixResetsTheStrayCount() {
        val engine = started().also { it.drive(1..10) }
        engine.offBy(11); engine.offBy(12); engine.at(13)
        assertTrue((engine.offBy(14) + engine.offBy(15)).asks().isEmpty())
    }

    @Test
    fun theThirdStrayFixAsksBeforeChangingTheRoute() {
        val engine = started().also { it.drive(1..10) }
        engine.offBy(11); engine.offBy(12)
        val commands = engine.offBy(13)
        assertTrue(commands.routes().isEmpty())
        assertEquals(listOf("코스를 벗어나고 있습니다. 잠깐 들르시나요?"), commands.says())
        assertEquals(listOf<Prompt?>(Prompt.DETOUR), commands.asks())
    }

    @Test
    fun sayingYesKeepsQuietAndKeepsTheRoute() {
        val engine = asked()
        val answered = engine.answer(true)
        assertEquals(listOf<Prompt?>(null), answered.asks())
        assertTrue(answered.routes().isEmpty())
        val later = (14..40).flatMap { engine.offBy(it) }
        assertTrue(later.routes().isEmpty())
        assertTrue(later.says().isEmpty())
    }

    @Test
    fun comingBackFromADetourIsAnnouncedOnce() {
        val engine = asked().also { it.answer(true) }
        assertTrue(engine.at(14).says().isEmpty())
        val back = engine.at(15)
        assertEquals(listOf("코스로 돌아왔습니다."), back.says())
        assertTrue(back.routes().isEmpty())
        assertTrue(engine.at(16).says().isEmpty())
    }

    @Test
    fun sayingNoRoutesBack() {
        val commands = asked().answer(false)
        assertEquals(1, commands.routes().size)
        assertEquals(listOf("코스로 돌아가는 길을 안내합니다."), commands.says())
        assertEquals(listOf<Prompt?>(null), commands.asks())
    }

    @Test
    fun noAnswerRoutesBackAfterAWhile() {
        val engine = asked()
        assertTrue((14..16).flatMap { engine.offBy(it) }.isEmpty())
        val commands = engine.offBy(17)
        assertEquals(1, commands.routes().size)
        assertEquals(listOf<Prompt?>(null), commands.asks())
    }

    @Test
    fun comingBackWhileBeingAskedTakesTheQuestionAway() {
        val commands = asked().at(14)
        assertEquals(listOf<Prompt?>(null), commands.asks())
        assertTrue(commands.routes().isEmpty())
        assertTrue(commands.says().isEmpty())
    }

    @Test
    fun aParkedCarIsNotStraying() {
        val engine = started().also { it.drive(1..10) }
        engine.park()
        assertTrue((1..10).flatMap { engine.offBy(11) }.isEmpty())
    }

    @Test
    fun anAnswerWithoutAQuestionIsIgnored() {
        val engine = started().also { it.drive(1..10) }
        assertTrue(engine.answer(true).isEmpty())
        assertTrue(engine.answer(false).isEmpty())
    }

    // 복귀는 한 번에, 앞쪽으로, 완충을 두고 (P4)

    /** 차 옆 자리에서 500m쯤 앞에 지점이 있으면 그 지점으로 돌아오고, 그 뒤 지점이 한 경로로 이어진다. */
    @Test
    fun theRejoinRouteStartsAtAStopAheadAndCarriesTheRest() {
        assertEquals(listOf(20, 40, 60), asked().answer(false).routes().single().indexes())
    }

    /**
     * 가까운 지점이 없으면 차 옆 자리에서 500m 앞의 코스 위 자리로 돌아온다. 다음 지점까지 건너뛰지 않는다.
     * 실제 코스는 지점이 5~15km 간격이라, 지점으로만 돌아오면 코스를 통째로 지나친다.
     */
    @Test
    fun withNoStopNearbyTheCarComesBackHalfAKilometreAhead() {
        val engine = started().also { it.drive(1..10) }
        repeat(3) { engine.offBy(22) }
        val leg = engine.answer(false).routes().single()
        assertEquals(listOf(28, 40, 60), leg.indexes())
        assertEquals("코스 복귀 지점", leg.waypoints.first().name)
    }

    /** 바로 옆의 지점은 지나친 것으로 본다. 뒤를 가리켜 유턴시키지 않는다. */
    @Test
    fun aStopRightBesideTheCarIsNotTheTarget() {
        val engine = started().also { it.drive(1..10) }
        repeat(3) { engine.offBy(18) }
        assertEquals(listOf(24, 40, 60), engine.answer(false).routes().single().indexes())
    }

    /** 코스가 되돌아와 차 가까이를 지나더라도 탐색 범위(2km, 약 22칸) 밖으로는 건너뛰지 않는다. */
    @Test
    fun theRejoinPointStaysWithinTheLookahead() {
        val engine = started().also { it.drive(1..10) }
        repeat(3) { engine.offBy(90) }
        assertEquals(40, engine.answer(false).routes().single().waypoints.first().pathIndex)
    }

    @Test
    fun returningNeedsNoNewRoute() {
        val engine = rejoining()
        val commands = engine.drive(14..25)
        assertTrue(commands.routes().isEmpty())
        assertEquals(listOf("코스로 돌아왔습니다."), commands.says())
    }

    /** 이탈 기준 안쪽이라도 복귀 기준 밖에서 오가는 동안에는 돌아왔다고 하지 않는다. */
    @Test
    fun hoveringAtTheEdgeIsNotAReturn() {
        val engine = rejoining()
        val commands = (0..5).flatMap { engine.offBy(14, degrees = 0.001) + engine.offBy(14) }
        assertTrue(commands.isEmpty())
    }

    @Test
    fun oneCloseFixIsNotYetAReturn() {
        val engine = rejoining()
        assertTrue(engine.at(14).isEmpty())
        assertTrue(engine.offBy(15).isEmpty())
        assertTrue(engine.at(15).isEmpty())
    }

    /** 복귀 지점을 지나쳐 허용 오차 안으로 들어오면 돌아온 것이다. 복귀 기준까지 붙지 않아도 된다. */
    @Test
    fun passingTheTargetInsideTheToleranceIsAReturn() {
        val commands = rejoining().offBy(22, degrees = 0.001)
        assertEquals(listOf("코스로 돌아왔습니다."), commands.says())
    }

    @Test
    fun aSkippedSectionIsAnnouncedOnce() {
        val three = Course("t", "셋", path, listOf(Section("첫", 0, 29), Section("가운데", 30, 59), Section("끝", 60, 100)))
        val engine = started(three).also { it.drive(1..25) }
        repeat(3) { engine.offBy(58) }
        assertEquals(53, engine.answer(false).routes().single().waypoints.first().pathIndex)
        val says = engine.at(65).says()
        assertEquals(listOf("코스로 돌아왔습니다.", "가운데 구간을 건너뛰었습니다.", "끝 구간입니다."), says)
        assertFalse("가운데 구간을 건너뛰었습니다." in engine.drive(66..100).says())
    }

    // 코스 밖에서 영영 못 돌아오지 않는다

    /** 들른 뒤 떠난 자리에서 탐색 범위(2km)를 넘겨 합류해도 돌아온 것이다. 지난 경유지는 빼고 다시 올린다. */
    @Test
    fun aDetourThatRejoinsFarAheadIsAReturn() {
        val engine = asked().also { it.answer(true) }
        assertTrue(engine.at(60).isEmpty())
        val commands = engine.at(64)
        assertEquals(listOf(80, 100), commands.routes().single().indexes())
        assertTrue("코스로 돌아왔습니다." in commands.says())
        assertTrue(engine.at(65).routes().isEmpty())
    }

    @Test
    fun ignoringTheRejoinRouteAndJoiningFarAheadIsAReturn() {
        val engine = rejoining()
        engine.at(70)
        val commands = engine.at(74)
        assertEquals(listOf(80, 100), commands.routes().single().indexes())
        assertTrue("코스로 돌아왔습니다." in commands.says())
    }

    /** 코스를 가로지르는 길에서는 코스를 따라 나아가지 않으므로 돌아온 것이 아니다. */
    @Test
    fun crossingTheCourseFarAheadIsNotAReturn() {
        val engine = asked().also { it.answer(true) }
        assertTrue((engine.at(60) + engine.offBy(60, degrees = 0.0002) + engine.offBy(60, degrees = -0.0002)).isEmpty())
    }

    /** 내비가 코스와 나란한 큰길로 안내하면 복귀 기준까지 붙지 않아도 시간이 지나면 코스 위로 본다. */
    @Test
    fun stayingInsideTheToleranceSettlesAsAReturn() {
        val engine = asked().also { it.answer(true) }
        val commands = (1..6).flatMap { engine.offBy(14 + it, degrees = 0.001) }
        assertEquals(listOf("코스로 돌아왔습니다."), commands.says())
    }

    /** 들르는 동안 지나친 경유지는 내비에 남아 있다. 돌아오면 빼고 다시 올린다. */
    @Test
    fun comingBackPastAStopFromADetourDropsThatStop() {
        val engine = asked().also { it.answer(true) }
        engine.at(22)
        assertEquals(listOf(40, 60, 80), engine.at(23).routes().single().indexes())
    }

    // 운전자가 넣은 경로 (P1)

    /** 멈춘 동안 더 달렸으면 지금 자리부터 이어간다. 지난 경유지로 되돌려 보내지 않는다. */
    @Test
    fun resumingAfterAPauseStartsFromWhereTheCarIsNow() {
        val engine = started().also { it.drive(1..10) }
        engine.onEvent(DriveEvent.RouteTakenOver)
        engine.at(30)
        assertEquals(listOf(40, 60, 80), engine.answer(true).routes().single().indexes())
    }

    @Test
    fun resumingFarAlongTheCourseDoesNotTurnBack() {
        val engine = started().also { it.drive(1..10) }
        engine.onEvent(DriveEvent.RouteTakenOver)
        engine.at(70)
        val commands = engine.answer(true)
        assertEquals(listOf(80, 100), commands.routes().single().indexes())
        assertEquals(listOf("코스를 이어갑니다."), commands.says())
    }

    /** 종점에 닿아 안내가 끝나며 오는 취소는 운전자가 끈 것이 아니다. 멈추면 완주가 막힌다. */
    @Test
    fun aCancelRightAtTheEndDoesNotPause() {
        val engine = started().also { it.drive(1..98) }
        assertTrue(engine.onEvent(DriveEvent.RouteCancelled).isEmpty())
        assertTrue(Publish(DriveEvent.CourseFinished) in engine.park())
    }

    @Test
    fun aRouteTheNavigationRefusedAsksToTryAgain() {
        val engine = started().also { it.drive(1..10) }
        val commands = engine.onEvent(DriveEvent.RouteFailed)
        assertEquals(listOf("내비에 경로를 올리지 못했습니다. 코스를 이어가려면 버튼을 눌러 주세요."), commands.says())
        assertEquals(listOf<Prompt?>(Prompt.RESUME), commands.asks())
        assertEquals(1, engine.answer(true).routes().size)
    }

    @Test
    fun aDriversOwnDestinationPausesTheCourse() {
        val engine = started().also { it.drive(1..10) }
        val commands = engine.onEvent(DriveEvent.RouteTakenOver)
        assertEquals(listOf("코스를 잠시 멈춥니다. 돌아오면 이어갑니다."), commands.says())
        assertEquals(listOf<Prompt?>(Prompt.RESUME), commands.asks())
        assertTrue(Publish(DriveEvent.CoursePaused) in commands)
        assertTrue(((11..30).flatMap { engine.offBy(it) } + engine.drive(31..60)).isEmpty())
    }

    @Test
    fun cancellingTheRoutePausesToo() {
        val engine = started().also { it.drive(1..10) }
        assertEquals(listOf<Prompt?>(Prompt.RESUME), engine.onEvent(DriveEvent.RouteCancelled).asks())
        assertTrue(engine.onEvent(DriveEvent.RouteTakenOver).isEmpty())
    }

    @Test
    fun arrivingAtTheDriversDestinationOffersToResume() {
        val engine = started().also { it.drive(1..10) }
        assertTrue(engine.onEvent(DriveEvent.DestinationArrived).isEmpty())
        engine.onEvent(DriveEvent.RouteTakenOver)
        assertEquals(listOf("코스를 이어갈까요?"), engine.onEvent(DriveEvent.DestinationArrived).says())
    }

    @Test
    fun resumingOnTheCoursePutsTheRestBackOnce() {
        val engine = started().also { it.drive(1..10) }
        engine.onEvent(DriveEvent.RouteTakenOver)
        val commands = engine.answer(true)
        assertEquals(listOf(20, 40, 60), commands.routes().single().indexes())
        assertEquals(listOf<Prompt?>(null), commands.asks())
        assertTrue(Publish(DriveEvent.SectionEntered(sections[0])) in commands)
        assertTrue(engine.drive(11..15).routes().isEmpty())
    }

    @Test
    fun resumingAwayFromTheCourseRoutesBackOnce() {
        val engine = started().also { it.drive(1..10) }
        engine.onEvent(DriveEvent.RouteTakenOver)
        engine.offBy(12, degrees = 0.05)
        val commands = engine.answer(true)
        assertEquals(listOf(20, 40, 60), commands.routes().single().indexes())
        assertEquals(listOf("코스로 돌아가는 길을 안내합니다."), commands.says())
    }

    /** 위치를 한 번도 받지 못한 채 멈췄다 이어가도 경로는 올라간다. */
    @Test
    fun resumingWithoutAnyFixStillRoutes() {
        val engine = started()
        engine.onEvent(DriveEvent.RouteTakenOver)
        assertEquals(listOf(20, 40, 60), engine.answer(true).routes().single().indexes())
    }

    @Test
    fun decliningToResumeStaysPaused() {
        val engine = started().also { it.drive(1..10) }
        engine.onEvent(DriveEvent.RouteTakenOver)
        assertTrue(engine.answer(false).isEmpty())
        assertTrue(engine.drive(11..20).isEmpty())
    }

    // 같은 구간의 반복 이탈 (P7)

    /** 0~20번 사이가 북쪽으로 약 220m 불룩한 길. 내비가 지름길을 고르는 구간이다. */
    private val bulge = (0..100).map { GeoPoint(37.0 + if (it < 20) 0.002 * (10 - kotlin.math.abs(it - 10)) / 10 else 0.0, 127.0 + it * 0.001) }
    private val bulged = Course("b", "불룩", bulge, sections)
    private fun CourseEngine.onBulge(index: Int) = onEvent(DriveEvent.LocationUpdated(bulge[index]))
    private fun CourseEngine.south(index: Int) = onEvent(DriveEvent.LocationUpdated(GeoPoint(36.99, bulge[index].lon)))

    /** 불룩한 구간에서 한 번 벗어났다 4번 지점으로 돌아온 상태. */
    private fun strayedOnce() = CourseEngine(tuning, stopsOf = { listOf(20, 40, 60, 80, 100).map { Stop("지점$it", bulge[it], it) } }).also { engine ->
        engine.onEvent(DriveEvent.CourseStarted(bulged))
        engine.onBulge(1); engine.onBulge(2)
        repeat(3) { engine.south(3) }
        engine.answer(false)
        engine.onBulge(3); engine.onBulge(4)
    }

    @Test
    fun theSecondStrayInAStretchAddsAStopWithoutAsking() {
        val engine = strayedOnce()
        engine.south(4); engine.south(4)
        val commands = engine.south(4)
        assertTrue(commands.asks().isEmpty())
        assertEquals(listOf(10, 20, 40), commands.routes().single().indexes())
    }

    @Test
    fun theThirdStrayInAStretchLeavesItToTheNavigation() {
        val engine = strayedOnce()
        repeat(3) { engine.south(4) }
        engine.onBulge(5); engine.onBulge(6)
        engine.south(7); engine.south(7)
        val commands = engine.south(7)
        assertEquals(listOf("내비 경로로 진행합니다."), commands.says())
        assertTrue(commands.routes().isEmpty())
        assertTrue((8..19).flatMap { engine.south(it) }.isEmpty())
    }

    @Test
    fun strayingIsWatchedAgainPastTheFreedStretch() {
        val engine = strayedOnce()
        repeat(3) { engine.south(4) }
        engine.onBulge(5); engine.onBulge(6)
        repeat(3) { engine.south(7) }
        engine.onBulge(21)
        engine.south(22); engine.south(22)
        assertEquals(listOf<Prompt?>(Prompt.DETOUR), engine.south(22).asks())
    }

    // 종점 반경 (P8)

    @Test
    fun reachingTheEndFinishesOnce() {
        val engine = started()
        val commands = engine.drive(1..100)
        assertTrue(Publish(DriveEvent.CourseFinished) in commands)
        assertTrue("코스를 끝까지 달렸습니다." in commands.says())
        assertTrue(engine.at(100).isEmpty())
    }

    @Test
    fun parkingJustShortOfTheEndFinishesTheCourse() {
        val engine = started().also { it.drive(1..97) }
        val commands = engine.park()
        assertTrue(Publish(DriveEvent.CourseFinished) in commands)
        assertTrue("코스를 끝까지 달렸습니다." in commands.says())
        assertTrue(engine.park().isEmpty())
    }

    @Test
    fun parkingMidCourseIsNotAFinish() {
        val engine = started().also { it.drive(1..50) }
        assertTrue(engine.park().isEmpty())
    }

    /** 출발점과 종점이 같은 코스에서 출발하자마자 세워도 끝난 것이 아니다. */
    @Test
    fun parkingAtTheStartOfARoundTripIsNotAFinish() {
        val engine = roundTripEngine()
        engine.onEvent(DriveEvent.LocationUpdated(outAndBack[1]))
        assertTrue(engine.park().isEmpty())
    }

    // 위치 끊김 (P9)

    /** 터널을 지나며 위치가 4.4km 끊겼다 돌아오면 이탈이 아니라 그 자리로 맞춘다. */
    @Test
    fun aGapInFixesMovesTheProgressInsteadOfStraying() {
        val engine = started().also { it.drive(1..10) }
        val commands = engine.at(60)
        assertEquals(1, commands.filterIsInstance<ShowProgress>().single().sectionIndex)
        assertTrue((engine.at(61) + engine.at(62)).asks().isEmpty())
    }

    // 코스 허용 오차와 따라가는 쪽의 능력

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
        engine.drive(1..10)
        assertTrue((1..3).flatMap { engine.offBy(10 + it, degrees = 0.015) }.asks().isEmpty())
    }

    @Test
    fun beyondTheCourseToleranceStillCountsAsStraying() {
        val wide = Course("w", "넓음", path, sections, toleranceMeters = 2_000.0)
        val engine = CourseEngine(DriveTuning(offCourseMeters = 1_300.0, lookaheadMeters = 2_000.0), stopsOf = { stops })
        engine.onEvent(DriveEvent.CourseStarted(wide))
        engine.drive(1..10)
        assertEquals(listOf<Prompt?>(Prompt.DETOUR), (1..3).flatMap { engine.offBy(10 + it, degrees = 0.03) }.asks())
    }

    @Test
    fun aFollowerWithMoreWaypointsGetsLongerLegs() {
        val roomy = CourseEngine(DriveTuning(), RouteAbility(maxWaypoints = 3), { stops })
        assertEquals(listOf(20, 40, 60, 80), roomy.onEvent(DriveEvent.CourseStarted(course)).routes().single().indexes())
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

    /** 코스를 끝낸 뒤에 오는 위치와 답은 아무 일도 하지 않는다. */
    @Test
    fun stoppingTheCourseEndsTheRun() {
        val engine = started().also { it.drive(1..10) }
        assertTrue(engine.onEvent(DriveEvent.CourseStopped).isEmpty())
        assertTrue((engine.at(11) + engine.answer(true) + engine.onEvent(DriveEvent.RouteTakenOver)).isEmpty())
        assertNull(engine.snapshot())
    }
}
