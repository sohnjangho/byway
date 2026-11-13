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
}
