package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MineKindTest {
    @Test
    fun madeCoursesAreNamedBeforeRuns() {
        assertEquals("내가 만든 코스", mineKind("m-abc234", recent = listOf("m-abc234")))
        assertEquals("달린 코스", mineKind("paldang-sunset", recent = listOf("paldang-sunset")))
        assertEquals("저장한 코스", mineKind("paldang-sunset", recent = emptyList()))
    }
}
