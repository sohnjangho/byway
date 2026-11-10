package com.byway.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    /** 정차할 때마다 회차가 하나 오른다. 3분할 안내는 같은 회차에서만 이어 출발한다. */
    @Test
    fun stopCountsEachNewPark() {
        val moving = DriveState(parked = false, stop = 3)
        assertEquals(4, withParked(moving, true).stop)
        assertEquals(4, withParked(withParked(moving, true), true).stop)
        assertEquals(3, withParked(moving, false).stop)
        assertFalse(withParked(moving, false).parked)
    }
}
