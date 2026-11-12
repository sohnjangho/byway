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

    /** 확대 지도는 핀 아래에 닉네임을 적는다. 핀에 닉네임이 실려야 한다. 내 핀은 그리지 않는다. */
    @Test
    fun partyPinsCarryNicknames() {
        val member = { id: String, name: String, me: Boolean ->
            com.byway.core.share.PartyMember(id, name, "avatar-01", demo = !me, me = me, sharing = true, starred = false)
        }
        val view = com.byway.core.share.PartyView(
            code = "ABC234", name = "파티", courseId = "c1", open = true, owner = true,
            members = listOf(member("m-me", "Road", true), member("demo-1", "시연용 1", false), member("demo-2", "시연용 2", false)),
            pins = listOf(
                com.byway.core.share.PartyPin("m-me", 37.5, 127.3),
                com.byway.core.share.PartyPin("demo-1", 37.51, 127.3),
                com.byway.core.share.PartyPin("demo-2", 37.49, 127.3),
            ),
        )
        assertEquals(listOf("시연용 1", "시연용 2"), partyPins(com.byway.service.DriveState(party = view)).map { it.name })
    }
}
