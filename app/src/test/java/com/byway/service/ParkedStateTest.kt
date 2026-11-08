package com.byway.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkedStateTest {
    @Test
    fun gearNotParkIsNeverParked() {
        listOf(true, false, null).forEach { assertFalse(parkedFrom(false, it)) }
    }

    @Test
    fun activeRestrictionBlocksParked() = assertFalse(parkedFrom(true, true))

    @Test
    fun parkAndNoRestrictionIsParked() = assertTrue(parkedFrom(true, false))

    @Test
    fun unknownRestrictionFallsBackToGear() = assertTrue(parkedFrom(true, null))
}
