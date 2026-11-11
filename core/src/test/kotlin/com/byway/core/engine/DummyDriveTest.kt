package com.byway.core.engine

import com.byway.core.course.Course
import com.byway.core.course.parseDummyCourses
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveCommand.FollowRoute
import com.byway.core.drive.DriveCommand.Say
import com.byway.core.drive.DriveCommand.ShowProgress
import com.byway.core.drive.DriveEvent
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyDriveTest {
    /** 동쪽으로 약 8.8km씩 세 다리. 표기 거리는 직선의 1.4배쯤인 37km. */
    private val course: Course = parseDummyCourses(
        """
        [{"id":"dummy-t","name":"시험 더미","region":"강원","km":37.0,
          "points":[{"name":"가","lat":37.0,"lon":127.0},{"name":"나","lat":37.0,"lon":127.1},
                    {"name":"다","lat":37.0,"lon":127.2},{"name":"라","lat":37.0,"lon":127.3}]}]
        """,
    ).single()

    private fun started(at: GeoPoint? = course.path.first()) =
        CourseEngine().let { engine -> engine to engine.onEvent(DriveEvent.CourseStarted(course, at = at)) }

    private fun CourseEngine.at(point: GeoPoint) = onEvent(DriveEvent.LocationUpdated(point))
    private fun List<DriveCommand>.says() = filterIsInstance<Say>().map { it.text }
    private val strayTalk = setOf(Messages.ASK_DETOUR, Messages.REJOINING, Messages.FREE, Messages.DETOUR_OK)

    @Test
    fun stopsAreThePointsThemselves() {
        val stops = planStops(course, DriveTuning().simplifyToleranceMeters)
        assertEquals(listOf("나", "다", "라"), stops.map { it.name })
        assertEquals(listOf(GeoPoint(37.0, 127.1), GeoPoint(37.0, 127.2), GeoPoint(37.0, 127.3)), stops.map { it.point })
    }

    @Test
    fun noStrayTalkFarFromStraightLine() {
        val (engine, first) = started()
        val talk = first.says().toMutableList()
        // 직선에서 북쪽으로 약 3km(0.027도)와 8km(0.072도) 떨어져 동쪽으로 간다. 실제 도로가 직선과 어긋나는 경우다.
        (1..30).forEach { talk += engine.at(GeoPoint(37.027, 127.0 + it * 0.003)).says() }
        (1..30).forEach { talk += engine.at(GeoPoint(37.072, 127.09 + it * 0.003)).says() }
        assertTrue(talk.toString(), talk.none { it in strayTalk })
    }

    @Test
    fun farFromStartGoesToStartLikeRealCourses() {
        // 출발 지점에서 남쪽으로 약 40km. 실제 코스처럼 출발점으로 먼저 안내한다.
        val (_, first) = started(GeoPoint(36.64, 127.0))
        assertTrue(first.says().toString(), Messages.TO_START in first.says())
        assertTrue(first.filterIsInstance<FollowRoute>().isNotEmpty())
    }

    @Test
    fun remainingIsScaledToDeclared() {
        val (engine, _) = started()
        val progress = engine.at(GeoPoint(37.0, 127.0005)).filterIsInstance<ShowProgress>().first()
        assertEquals(37_000.0, progress.remainingMeters, 37_000.0 * 0.01)
    }

    @Test
    fun finishesOnlyCloseToLastPoint() {
        val (engine, _) = started()
        var commands = (1..55).flatMap { engine.at(GeoPoint(37.0, 127.0 + it * 0.005)) }
        // 끝 지점 동쪽 약 4km 바깥, 직선 위로 비치면 마지막 점이지만 아직 끝이 아니다.
        commands = commands + engine.at(GeoPoint(37.0, 127.345))
        assertFalse(commands.filterIsInstance<DriveCommand.Publish>().any { it.event == DriveEvent.CourseFinished })
        val end = engine.at(GeoPoint(37.0, 127.3005))
        assertTrue(end.filterIsInstance<DriveCommand.Publish>().any { it.event == DriveEvent.CourseFinished })
    }
}
