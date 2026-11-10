package com.byway.ui

import com.byway.ui.party.pinGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyPinsTest {
    /** 물방울 끝이 위치를 가리키고, 원형 아바타는 그 위에 뜬다. 원은 끝을 덮지 않는다. */
    @Test
    fun pinCircleSitsAboveTip() {
        val pin = pinGeometry(tipX = 100f, tipY = 200f, radius = 20f)
        assertEquals(100f, pin.centerX)
        assertTrue(pin.centerY < 200f - pin.outerRadius)
        assertTrue(pin.avatarRadius < pin.outerRadius)
        assertEquals(20f, pin.avatarRadius)
    }

    @Test
    fun partyScreenOnlyWhenParked() {
        assertEquals(Screen.Party, screenFor(com.byway.service.DriveState(parked = true), Nav(tab = Tab.MINE, party = true)))
        assertEquals(Screen.Waiting, screenFor(com.byway.service.DriveState(parked = false), Nav(tab = Tab.MINE, party = true)))
    }
}
