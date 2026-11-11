package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.RankBy
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 더미 코스는 목록에 들지만 랭킹·자동 추천·홈 띠에서는 빠진다(더 많은 코스 설계 8절). */
class CourseBookDummyTest {
    private fun real(id: String, curvy: Boolean = false): Course {
        val path = if (curvy) {
            (0..60).map { GeoPoint(37.0 + (it % 2) * 0.004, 127.0 + it * 0.002) }
        } else {
            listOf(GeoPoint(37.0, 127.0), GeoPoint(37.05, 127.0))
        }
        return Course(id, id, path, listOf(Section(id, 0, path.lastIndex)))
    }

    /** 굽이진 길처럼 보이게 지그재그로 두어도 랭킹에 들면 안 된다. */
    private val dummy = Course(
        "dummy-1-01", "더미", (0..60).map { GeoPoint(37.0 + (it % 2) * 0.01, 127.0 + it * 0.002) },
        listOf(Section("가", 0, 30), Section("나", 31, 60)), dummy = true, declaredMeters = 40_000.0,
    )
    private val book = CourseBook(listOf(real("a"), real("b", curvy = true), dummy))

    @Test
    fun rankingLeavesDummiesOut() {
        RankBy.entries.forEach { by -> assertTrue(book.ranked(by).none { it.course.dummy }) }
        assertEquals(40_000.0, book.facts(dummy).meters, 1e-6)
    }

    @Test
    fun suggestionNeverDummy() {
        (0..23).forEach { hour -> assertFalse(book.suggested(hour).dummy) }
    }

    @Test
    fun featuredAddsOnlyChosenDummy() {
        assertEquals(listOf("a", "b"), book.featured(null).map { it.id })
        assertEquals(listOf("a", "b"), book.featured("a").map { it.id })
        assertEquals(listOf("dummy-1-01", "a", "b"), book.featured("dummy-1-01").map { it.id })
    }

    @Test
    fun sectionMetersSumToDeclared() {
        assertEquals(40_000.0, book.sectionMeters(dummy).sum(), 400.0)
    }
}
