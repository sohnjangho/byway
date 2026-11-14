package com.byway.service

import com.byway.core.gleo.GleoNames
import com.byway.core.gleo.GleoReply
import com.byway.core.gleo.GleoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoReceiverTest {
    /** 6초 타이머와 늦게 끝난 판단이 겹쳐도 Gleo 에는 한 번만 답한다. */
    @Test
    fun replyOnce() {
        val sent = mutableListOf<GleoReply>()
        val once = ReplyOnce { sent += it }
        assertTrue(once.reply(GleoReply(GleoNames.FIND, GleoStatus.FAIL, "A timeout occurred while processing the request.")))
        assertFalse(once.reply(GleoReply(GleoNames.FIND, GleoStatus.SUCCESS, "late")))
        assertEquals(listOf(GleoStatus.FAIL), sent.map { it.status })
    }

    @Test
    fun memoryForgetsAfterTenMinutes() {
        GleoMemory.remember(listOf("a"), 0)
        assertEquals(listOf("a"), GleoMemory.recall(599_999))
        assertEquals(emptyList<String>(), GleoMemory.recall(600_001))
    }

    /** 주행 서비스가 받는 위치는 믿고, 둘러보기 위치는 2분 안의 것만 쓴다. 오래된 자리로 "근처"를 계산하지 않는다(검토 I4). */
    @Test
    fun positionMustBeFresh() {
        val drive = com.byway.core.geo.GeoPoint(37.0, 127.0)
        val browse = com.byway.core.geo.GeoPoint(35.0, 129.0)
        assertEquals(drive, voicePosition(drive, driveLive = true, browse = browse, browseAgeMs = 0))
        assertEquals(browse, voicePosition(drive, driveLive = false, browse = browse, browseAgeMs = 119_000))
        assertEquals(null, voicePosition(drive, driveLive = false, browse = browse, browseAgeMs = 121_000))
        assertEquals(null, voicePosition(null, driveLive = false, browse = null, browseAgeMs = null))
    }

    /** 실행 중 예외가 나도 앱이 죽지 않고 실패로 한 번 답한다(검토 M2). */
    @Test
    fun effectFailureBecomesFailReply() {
        val ok = GleoReply(GleoNames.CONTROL, GleoStatus.SUCCESS, "done")
        assertEquals(ok, safely(GleoNames.CONTROL, report = {}) { ok })
        val failed = safely(GleoNames.CONTROL, report = {}) { throw IllegalArgumentException("boom") }
        assertEquals(GleoStatus.FAIL, failed.status)
        assertEquals(GleoNames.CONTROL, failed.function)
    }
}
