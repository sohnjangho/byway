package com.byway.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinMemoryTest {
    @Test
    fun theValueBeforeTheFirstWriteComesBack() {
        val memory = CabinMemory()
        memory.wrote(CabinItem.WINDOW, was = 0, value = 2)
        assertEquals(0, memory.restores(CabinItem.WINDOW, now = 2))
    }

    /** 구간이 바뀌어 여러 번 써도 되돌릴 값은 맨 처음 값이다. */
    @Test
    fun laterWritesKeepTheFirstValue() {
        val memory = CabinMemory()
        memory.wrote(CabinItem.LIGHT, was = 10, value = 40)
        memory.wrote(CabinItem.LIGHT, was = 40, value = 70)
        assertEquals(10, memory.restores(CabinItem.LIGHT, now = 70))
    }

    /** 운전자가 창문을 직접 닫았으면 다시 열지 않는다. */
    @Test
    fun anItemTheDriverChangedIsLeftAlone() {
        val memory = CabinMemory()
        memory.wrote(CabinItem.WINDOW, was = 5, value = 2)
        assertNull(memory.restores(CabinItem.WINDOW, now = 0))
    }

    @Test
    fun anItemAlreadyAtItsFirstValueNeedsNothing() {
        val memory = CabinMemory()
        memory.wrote(CabinItem.FAN, was = 3, value = 3)
        assertNull(memory.restores(CabinItem.FAN, now = 3))
    }

    @Test
    fun anItemWeNeverWroteIsLeftAlone() {
        assertNull(CabinMemory().restores(CabinItem.FAN, now = 3))
    }

    @Test
    fun clearingForgetsEverything() {
        val memory = CabinMemory().also { it.wrote(CabinItem.WINDOW, 0, 2) }
        memory.clear()
        assertTrue(memory.items.isEmpty())
        assertNull(memory.restores(CabinItem.WINDOW, now = 2))
    }

    /** 앱이 꺼졌다 켜져도 되돌릴 수 있다. */
    @Test
    fun theMemorySurvivesBeingWrittenDown() {
        val memory = CabinMemory()
        memory.wrote(CabinItem.WINDOW, was = 0, value = 2)
        memory.wrote(CabinItem.LIGHT, was = 10, value = 70)
        val back = CabinMemory.decode(memory.encode())
        assertEquals(setOf(CabinItem.WINDOW, CabinItem.LIGHT), back.items)
        assertEquals(0, back.restores(CabinItem.WINDOW, now = 2))
        assertEquals(10, back.restores(CabinItem.LIGHT, now = 70))
    }

    @Test
    fun brokenTextRestoresNothing() {
        assertTrue(CabinMemory.decode("").items.isEmpty())
        assertTrue(CabinMemory.decode("DOOR:1:2,WINDOW:x:2,FAN").items.isEmpty())
    }
}
