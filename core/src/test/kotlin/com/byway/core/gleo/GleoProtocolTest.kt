package com.byway.core.gleo

import com.byway.core.course.LengthBand
import com.byway.core.course.MOOD_KEYS
import com.byway.core.course.RankBy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoProtocolTest {
    /** Gleo 문서 예시는 JSON 을 따옴표로 한 번 더 감싸 보낸다. 둘 다 읽어야 한다. */
    @Test
    fun paramsReadPlainAndQuotedJson() {
        assertEquals("노을", parseGleoParams("""{"searchTerm":"노을"}""")["searchTerm"]!!.content)
        assertEquals("미팅", parseGleoParams("\"{\\\"title\\\":\\\"미팅\\\"}\"")["title"]!!.content)
        assertEquals(25.0, parseGleoParams("""{"nearbyKm":25}""")["nearbyKm"]!!.double, 0.0)
    }

    @Test
    fun paramsTolerateGarbage() {
        listOf(null, "", "   ", "{", "[1,2]", "\"plain\"").forEach { assertTrue(it.toString(), parseGleoParams(it).isEmpty()) }
    }

    @Test
    fun replyJsonMatchesCallbackSchema() {
        val reply = GleoReply(GleoNames.START, GleoStatus.SUCCESS, "Started.", listOf(mapOf("name" to "팔당 노을", "km" to 50.3)), GleoMode.SILENT)
        val json = Json.parseToJsonElement(reply.toJson()).jsonObject
        assertEquals("START_DRIVING_COURSE", json["functionName"]!!.jsonPrimitive.content)
        assertEquals("success", json["status"]!!.jsonPrimitive.content)
        assertEquals("silent", json["mode"]!!.jsonPrimitive.content)
        assertTrue(json["data"]!!.jsonPrimitive.isString)
        val first = Json.parseToJsonElement(json["data"]!!.jsonPrimitive.content).jsonArray[0].jsonObject
        assertEquals("팔당 노을", first["name"]!!.jsonPrimitive.content)
        assertEquals(50.3, first["km"]!!.jsonPrimitive.double, 0.0)
        val bare = Json.parseToJsonElement(GleoReply(GleoNames.FIND, GleoStatus.FAIL, "No.").toJson()).jsonObject
        assertFalse("data" in bare)
        assertFalse("mode" in bare)
    }

    @Test
    fun enumsCoverEveryKey() {
        assertEquals(MOOD_KEYS.toSet(), MOOD_ENUM.values.toSet())
        assertEquals(12, MOOD_ENUM.size)
        assertEquals(LengthBand.entries.toSet(), LENGTH_ENUM.values.toSet())
        assertEquals(RankBy.entries.toSet(), RANK_ENUM.values.toSet())
        assertEquals(12, COMMANDS.size)
        assertEquals(listOf(GleoNames.FIND, GleoNames.START, GleoNames.CONTROL), GleoNames.ALL)
    }
}
