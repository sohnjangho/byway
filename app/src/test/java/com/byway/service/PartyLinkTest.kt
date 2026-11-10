package com.byway.service

import com.byway.core.geo.GeoPoint
import com.byway.core.share.PartyMember
import com.byway.core.share.PartyPin
import com.byway.core.share.PartyView
import com.byway.core.share.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyLinkTest {
    private val me = PartyMember("m-me", "하나", "avatar-01", demo = false, me = true, sharing = true, starred = false)
    private val friend = PartyMember("m-2", "둘", "avatar-02", demo = false, me = false, sharing = false, starred = false)
    private fun view(vararg pins: PartyPin) = PartyView("ABCDEF", "하나의 파티", "paldang-sunset", true, true, listOf(me, friend), pins.toList())

    @Test
    fun endedPartyClearsPins() {
        val tick = partyTick(GeoPoint(37.5, 127.0), sharing = true, send = { Result.success(Unit) }, fetch = { Result.failure(ShareError.Http(404, "없음")) })
        assertEquals(PartyTick.Ended, tick)
    }

    /** 주인이 끝낸 파티는 별표한 멤버에게도 끝난 것이다. 다시 열기는 파티 밖 화면의 즐겨찾는 파티에서 한다. */
    @Test
    fun closedPartyCountsAsEnded() {
        val tick = partyTick(null, sharing = false, send = { Result.success(Unit) }, fetch = { Result.success(view().copy(open = false)) })
        assertEquals(PartyTick.Ended, tick)
    }

    @Test
    fun unreachableKeepsTheParty() {
        val tick = partyTick(null, sharing = false, send = { Result.success(Unit) }, fetch = { Result.failure(ShareError.Offline()) })
        assertEquals(PartyTick.Unreachable, tick)
    }

    @Test
    fun sendsOnlyWhenSharing() {
        val sent = mutableListOf<Position>()
        val here = GeoPoint(37.5, 127.0)
        partyTick(here, sharing = false, send = { sent += it; Result.success(Unit) }, fetch = { Result.success(view()) })
        partyTick(null, sharing = true, send = { sent += it; Result.success(Unit) }, fetch = { Result.success(view()) })
        assertTrue(sent.isEmpty())
        val tick = partyTick(here, sharing = true, send = { sent += it; Result.success(Unit) }, fetch = { Result.success(view()) })
        assertEquals(listOf(Position(37.5, 127.0)), sent)
        assertEquals(PartyTick.Seen(view()), tick)
    }

    @Test
    fun stalePinsDropAfterThirtySeconds() {
        val pins = livePins(view(PartyPin("m-me", 37.5, 127.0, 0), PartyPin("m-2", 37.6, 127.1, 29_000), PartyPin("demo-1", 37.7, 127.2, 31_000)))
        assertEquals(listOf("m-2"), pins.map { it.memberId })
    }
}
