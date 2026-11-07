package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverFileTest {
    private val both = setOf("a.jpg", "a-night.jpg")
    private val dayOnly = setOf("a.jpg")

    @Test
    fun dayUsesTheDayCover() {
        assertEquals("a.jpg", coverFile("a", night = false, both))
    }

    @Test
    fun nightUsesTheNightCover() {
        assertEquals("a-night.jpg", coverFile("a", night = true, both))
    }

    @Test
    fun nightFallsBackToTheDayCover() {
        assertEquals("a.jpg", coverFile("a", night = true, dayOnly))
    }

    /** 야간 파일만 있는 경우는 없다. 있어도 주간에 어두운 사진을 쓰지 않는다. */
    @Test
    fun dayDoesNotBorrowTheNightCover() {
        assertNull(coverFile("a", night = false, setOf("a-night.jpg")))
    }

    @Test
    fun aCourseWithoutCoverHasNoFile() {
        assertNull(coverFile("b", night = true, both))
    }
}
