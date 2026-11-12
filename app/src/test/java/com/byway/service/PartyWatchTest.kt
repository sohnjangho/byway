package com.byway.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyWatchTest {
    /** 리더가 고른 코스를 물으려고 파티를 받는 것은 정차 중, 코스가 달리지 않을 때, 파티에 들어가 있을 때뿐이다(함께달리기 설계 6-6, 6-10.1). */
    @Test
    fun watchesOnlyWhenParkedIdleAndInAParty() {
        assertTrue(shouldWatch(parked = true, running = false, partyCode = "ABC234", online = true))
        assertFalse(shouldWatch(parked = false, running = false, partyCode = "ABC234", online = true))
        assertFalse(shouldWatch(parked = true, running = true, partyCode = "ABC234", online = true))
        assertFalse(shouldWatch(parked = true, running = false, partyCode = null, online = true))
        assertFalse(shouldWatch(parked = true, running = false, partyCode = "ABC234", online = false))
    }
}
