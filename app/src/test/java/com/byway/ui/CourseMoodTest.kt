package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 코스가 정해진 화면의 분위기 배경(화면 다듬기 설계 4절). */
class CourseMoodTest {
    private val path = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.1, 127.1), GeoPoint(37.2, 127.2))
    private val course = Course(
        "c1", "코스", path,
        listOf(Section("숲길", 0, 1, "forest"), Section("강변", 1, 2, "river")),
        sceneryCredit = "사진: 누구 (CC BY-SA 4.0, Wikimedia Commons)",
    )

    @Test
    fun moodFollowsTheSection() {
        assertEquals(DayPalette.airColor("forest"), moodTint(course, 0, DayPalette))
        assertEquals(DayPalette.airColor("river"), moodTint(course, 1, DayPalette))
        assertEquals(NightPalette.airColor("river"), moodTint(course, 1, NightPalette))
    }

    @Test
    fun outOfRangeSectionUsesTheFirst() {
        assertEquals(DayPalette.airColor("forest"), moodTint(course, 5, DayPalette))
        assertEquals(DayPalette.airColor("forest"), moodTint(course, -1, DayPalette))
    }

    /** 사진이 없는 코스는 색만 깐다. 그때는 적을 사진 출처도 없다. 사진을 흐리게 깔면 변형을 밝힌다. */
    @Test
    fun moodWithoutPhotoIsColorOnly() {
        assertNull(moodPhotoCredit(course, hasPhoto = false))
        assertNull(moodPhotoCredit(course.copy(sceneryCredit = ""), hasPhoto = true))
        assertEquals("사진 누구 · CC BY-SA 4.0 · 변형", moodPhotoCredit(course, hasPhoto = true))
    }
}
