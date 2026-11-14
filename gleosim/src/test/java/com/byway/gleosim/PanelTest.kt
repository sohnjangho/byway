package com.byway.gleosim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gleo 패널 템플릿(목록형)에 들어갈 내용과 답 말풍선 문장. Gleo 디자인 가이드의 List Type 을 따른다. */
class PanelTest {
    private fun response(function: String, status: String, message: String, data: String? = null, mode: String? = null): String {
        val fields = mutableListOf(""""functionName":"$function"""", """"status":"$status"""", """"message":"$message"""")
        data?.let { fields += """"data":${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(it))}""" }
        mode?.let { fields += """"mode":"$it"""" }
        return "{" + fields.joinToString(",") + "}"
    }

    @Test
    fun findBecomesNumberedList() {
        val data = """[{"rank":1,"name":"호수 잇는 길","region":"수도권","km":25.3,"awayKm":5.0,"moods":["데이트","가족","봄","노을"]},{"rank":2,"name":"팔당 노을","region":"수도권","km":50.3,"moods":["데이트"]}]"""
        val panel = panelFor(response("FIND_DRIVING_COURSES", "success", "Found 63 courses.", data))!!
        assertEquals(listOf("호수 잇는 길", "팔당 노을"), panel.items.map { it.title })
        assertEquals(1, panel.items[0].number)
        assertEquals("25.3km · 수도권 · 5km 거리", panel.items[0].meta)
        assertEquals(listOf("데이트", "가족", "봄"), panel.items[0].tags)
        assertEquals("50.3km · 수도권", panel.items[1].meta)
    }

    @Test
    fun statusBecomesOneCard() {
        val data = """[{"course":"팔당 노을","remainingKm":19.3,"section":"2/3","sectionName":"하남 팔당대교","nextSectionName":"팔당 강변"}]"""
        val panel = panelFor(response("CONTROL_BYWAY_DRIVE", "success", "Here is the progress.", data))!!
        val card = panel.items.single()
        assertEquals("팔당 노을", card.title)
        assertNull(card.number)
        assertEquals("남은 거리 19.3km · 구간 2/3", card.meta)
        assertEquals(listOf("하남 팔당대교"), card.tags)
    }

    @Test
    fun startBecomesCourseCard() {
        val data = """[{"name":"호수 잇는 길","km":25.3,"sections":4,"firstSection":"운중동 하오고개"}]"""
        val card = panelFor(response("START_DRIVING_COURSE", "success", "Started.", data, "silent"))!!.items.single()
        assertEquals("호수 잇는 길", card.title)
        assertEquals("25.3km · 구간 4개", card.meta)
        assertEquals(listOf("운중동 하오고개"), card.tags)
    }

    @Test
    fun noDataNoPanel() {
        assertNull(panelFor(response("CONTROL_BYWAY_DRIVE", "success", "Okay, the course will wait while you stop by.")))
        assertNull(panelFor(response("FIND_DRIVING_COURSES", "fail", "x")))
        assertNull(panelFor("not json"))
    }

    /** 조작 명령의 답은 에움길이 보낸 영어 문장을 자연스러운 한국어로 옮긴다. */
    @Test
    fun controlRepliesAreKorean() {
        assertEquals("다녀오세요. 코스는 기다리고 있을게요", koreanReply(response("CONTROL_BYWAY_DRIVE", "success", "Okay, the course will wait while you stop by.")))
        assertEquals("코스로 돌아가는 길을 안내할게요", koreanReply(response("CONTROL_BYWAY_DRIVE", "success", "Guiding the way back to the course.")))
        assertEquals("음성 안내를 껐어요", koreanReply(response("CONTROL_BYWAY_DRIVE", "success", "Voice guidance is now off.")))
        assertTrue(koreanReply(response("CONTROL_BYWAY_DRIVE", "success", "The course 팔당 노을 has ended.")).startsWith("코스를 끝냈어요"))
    }
}
