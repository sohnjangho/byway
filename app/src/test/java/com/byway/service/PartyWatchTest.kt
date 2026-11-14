package com.byway.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyWatchTest {
    /** 리더가 고른 코스를 물으려고 파티를 받는 것은 정차 중, 코스가 달리지 않을 때, 서버가 있을 때뿐이다(함께달리기 설계 6-6, 6-10.1). */
    @Test
    fun watchesOnlyWhenParkedIdleAndOnline() {
        assertTrue(shouldWatch(parked = true, running = false, online = true))
        assertFalse(shouldWatch(parked = false, running = false, online = true))
        assertFalse(shouldWatch(parked = true, running = true, online = true))
        assertFalse(shouldWatch(parked = true, running = false, online = false))
    }

    /**
     * 아직 파티에 없어도 정차 중이면 감시를 켜 둔다. 함께 달리기 탭에서 코드로 들어가면 탭만 다시 그려지고 화면 전체는
     * 다시 정해지지 않아, 감시가 꺼진 채 리더가 고른 코스를 묻지 못했다(11-13 T1 촬영에서 발견). 파티가 없는 동안 감시는 서버에 묻지 않는다.
     */
    @Test
    fun watchesBeforeJoiningSoAJoinInTheTogetherTabIsNoticed() {
        assertTrue(shouldWatch(parked = true, running = false, online = true))
    }
}
