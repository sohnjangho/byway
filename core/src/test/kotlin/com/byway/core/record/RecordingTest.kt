package com.byway.core.record

import com.byway.core.course.parseCourse
import com.byway.core.course.writeCourse
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.engine.CourseEngine
import com.byway.core.geo.GeoPoint
import com.byway.core.share.CODE_ALPHABET
import com.byway.core.share.isCode
import com.byway.core.share.newCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecordingTest {
    /** 위도 37.5 에서 동쪽으로 [stepMeters] 간격의 직선 길. */
    private fun straight(meters: Double, stepMeters: Double = 100.0, startMeters: Double = 0.0): List<GeoPoint> {
        val degPerMeter = 1.0 / 88_200.0
        val count = (meters / stepMeters).toInt()
        return (0..count).map { GeoPoint(37.5, 127.0 + (startMeters + it * stepMeters) * degPerMeter) }
    }

    @Test
    fun trimsHalfKilometerFromBothEnds() {
        val path = straight(20_000.0)
        val trimmed = trimEnds(path, 500.0)
        assertTrue(trimmed.all { it.distanceTo(path.first()) >= 499.0 && it.distanceTo(path.last()) >= 499.0 })
        assertTrue(trimmed.first().distanceTo(path.first()) < 700.0)
        assertTrue(trimmed.last().distanceTo(path.last()) < 700.0)
    }

    @Test
    fun tooShortAfterTrimIsRejected() {
        val result = makeCourse("m-t", "짧은 길", "city", straight(5_800.0))
        assertTrue(result is Recorded.TooShort)
        assertEquals(4_800.0, (result as Recorded.TooShort).meters, 150.0)
    }

    @Test
    fun sectionsEveryFiveKilometersCoverThePath() {
        val made = makeCourse("m-t", "강변 길", "river", straight(21_000.0)) as Recorded.Made
        val course = made.course
        assertTrue(course.sections.size in 4..5)
        assertEquals((1..course.sections.size).map { "${it}구간" }, course.sections.map { it.name })
        assertTrue(course.sections.all { it.mood == "river" })
        assertEquals(course, parseCourse(writeCourse(course)))
    }

    /** 지하 주차장이나 첫 위치가 수백 m 튀어도 집(두 번째 점)이 남지 않는다. */
    @Test
    fun aJumpAtTheEndsDoesNotSaveHome() {
        val home = GeoPoint(37.5, 127.0)
        val bogus = GeoPoint(37.5 + 900.0 / 111_000.0, 127.0)
        val path = listOf(bogus) + straight(8_000.0) + listOf(GeoPoint(37.5 - 900.0 / 111_000.0, 127.0 + 8_000.0 / 88_200.0))
        val trimmed = trimEnds(path, 500.0)
        assertTrue(trimmed.isNotEmpty())
        assertTrue(trimmed.all { it.distanceTo(home) >= 499.0 && it.distanceTo(path[path.lastIndex - 1]) >= 499.0 })
    }

    @Test
    fun jumpIsNotCounted() {
        val path = straight(4_000.0) + straight(3_000.0, startMeters = 7_000.0)
        // 시험 길은 경도 1도를 88.2km로 어림해 만들어서 실제 거리와 0.2% 안쪽으로 다르다. 튐 3km가 들어가면 10km가 된다.
        assertEquals(7_000.0, drivenMeters(path, 500.0), 20.0)
    }

    @Test
    fun badNameIsRejected() {
        assertTrue(makeCourse("m-t", " ", "city", straight(21_000.0)) is Recorded.BadName)
        assertTrue(makeCourse("m-t", "가".repeat(21), "city", straight(21_000.0)) is Recorded.BadName)
        assertEquals(null, nameProblem("가".repeat(20)))
    }

    @Test
    fun madeCourseHasDriverAttribution() {
        val made = makeCourse("m-t", "숲길", "forest", straight(21_000.0)) as Recorded.Made
        assertEquals("에움길 운전자 기록", made.course.attribution)
        assertEquals("숲길", made.course.name)
    }

    @Test
    fun madeCourseCanBeDrivenToTheEnd() {
        val fixes = straight(10_000.0, stepMeters = 15.0) +
            (1..800).map { GeoPoint(37.5 + it * 15.0 / 111_000.0, 127.0 + 10_000.0 / 88_200.0) }
        val course = (makeCourse("m-t", "꺾인 길", "city", fixes) as Recorded.Made).course
        val engine = CourseEngine()
        engine.onEvent(DriveEvent.CourseStarted(course))
        val commands = course.path.flatMap { engine.onEvent(DriveEvent.LocationUpdated(it)) }
        assertTrue(DriveCommand.Publish(DriveEvent.CourseFinished) in commands)
        assertTrue(commands.filterIsInstance<DriveCommand.Say>().none { "벗어났습니다" in it.text })
    }

    @Test
    fun codeUsesOnlyClearCharacters() {
        val random = Random(7)
        val codes = List(1_000) { newCode(random) }
        assertTrue(codes.all(::isCode))
        assertTrue(codes.none { code -> code.any { it in "01OI" } })
        assertFalse("0" in CODE_ALPHABET || "O" in CODE_ALPHABET)
        assertFalse(isCode("ABC12"))
    }
}
