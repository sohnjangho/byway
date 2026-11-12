package com.byway.ui

import com.byway.core.course.CourseFilter
import com.byway.core.course.LengthBand
import com.byway.core.course.MOOD_KEYS
import com.byway.core.course.NearRange
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseCopyTest {
    @Test
    fun moodLabelsInScreenOrder() {
        assertEquals(
            listOf("혼자", "데이트", "가족", "봄", "여름", "가을", "겨울", "비 오는 날", "일출", "낮", "노을", "밤"),
            MOOD_KEYS.map(::moodLabel),
        )
        assertEquals("비 오는 날", moodLabel("weathers:rain"))
        assertEquals("노을", moodLabel("times:sunset"))
    }

    @Test
    fun lengthAndNearLabels() {
        assertEquals(listOf("30km 이하", "30~60km", "60~100km", "100km 넘게"), LengthBand.entries.map(::lengthLabel))
        assertEquals(listOf("25km", "50km", "75km"), NearRange.entries.map(::nearLabel))
    }

    @Test
    fun filterButtonShowsNameOrValue() {
        assertEquals("내 주변", filterButtonText("near", CourseFilter()))
        assertEquals("50km 안", filterButtonText("near", CourseFilter(near = NearRange.KM50)))
        assertEquals("무드", filterButtonText("mood", CourseFilter()))
        assertEquals("비 오는 날", filterButtonText("mood", CourseFilter(mood = "weathers:rain")))
        assertEquals("거리", filterButtonText("length", CourseFilter()))
        assertEquals("100km 넘게", filterButtonText("length", CourseFilter(length = LengthBand.OVER_100)))
        assertEquals("지역", filterButtonText("region", CourseFilter()))
        assertEquals("제주", filterButtonText("region", CourseFilter(region = "제주")))
        assertEquals("풍경", filterButtonText("theme", CourseFilter()))
        assertEquals("바다", filterButtonText("theme", CourseFilter(theme = "coast")))
    }

    @Test
    fun awayIsRoundedKm() {
        assertEquals("12km 거리", awayText(12_400.0))
        assertEquals("1km 거리", awayText(300.0))
    }

    /** 위치를 모르면 내 주변은 걸리지 않는다. 걸어 두었거나 줄을 펼친 동안에는 이유를 계속 보인다(검토 I-2). */
    @Test
    fun nearNoteShowsWhileNearCannotWork() {
        assertEquals(NO_POSITION, nearNote(CourseFilter(near = NearRange.KM25), null, located = false))
        assertEquals(NO_POSITION, nearNote(CourseFilter(), "near", located = false))
        assertEquals(null, nearNote(CourseFilter(near = NearRange.KM25), null, located = true))
        assertEquals(null, nearNote(CourseFilter(), "mood", located = false))
    }

    /** 목록 한 줄의 둘째 줄: 더미 표시, 내 주변 거리, 지역 · 거리 · 추천. */
    @Test
    fun rowLineJoinsRegionDistanceAndPick() {
        val path = listOf(com.byway.core.geo.GeoPoint(37.0, 127.0), com.byway.core.geo.GeoPoint(37.0, 127.1))
        val dummy = com.byway.core.course.Course(
            "dummy-1-01", "더미 길", path, listOf(com.byway.core.course.Section("끝", 0, 1)),
            tags = com.byway.core.course.CourseTags(seasons = listOf("spring")), region = "수도권", dummy = true, declaredMeters = 85_000.0,
        )
        assertEquals("더미 · 12km 거리 · 수도권 · 85.0km · 봄에 추천", rowLine(dummy, 85_000.0, 12_400.0, showAway = true))
        assertEquals("더미 · 수도권 · 85.0km · 봄에 추천", rowLine(dummy, 85_000.0, 12_400.0, showAway = false))
        assertEquals("수도권 · 85.0km · 봄에 추천", rowLine(dummy.copy(dummy = false), 85_000.0, null, showAway = true))
    }
}
