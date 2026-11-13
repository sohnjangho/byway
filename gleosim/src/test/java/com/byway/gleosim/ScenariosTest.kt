package com.byway.gleosim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenariosTest {
    /** 에움길 스키마의 함수 이름. 흉내 앱은 core 를 쓰지 않으므로 문자열로 고정해 확인한다. */
    private val functions = setOf("FIND_DRIVING_COURSES", "START_DRIVING_COURSE", "CONTROL_BYWAY_DRIVE")

    @Test
    fun scenariosUseRealFunctionNames() {
        assertEquals(8, SCENARIOS.size)
        assertEquals("노을 보기 좋은 코스 찾아줘", SCENARIOS.first().utterance)
        SCENARIOS.forEach { assertTrue(it.function, it.function in functions) }
    }

    /** 시연 영상의 다른 장면과 같은 코스(팔당 노을)로 이름을 불러 출발한다(기획서 세션 지시 11-13 4-2, PM). */
    @Test
    fun startScenarioNamesPaldang() {
        val start = SCENARIOS[1]
        assertEquals("팔당 노을로 출발해줘", start.utterance)
        assertEquals("START_DRIVING_COURSE", start.function)
        assertEquals("팔당 노을", Json.parseToJsonElement(start.paramJson).jsonObject["courseName"]?.jsonPrimitive?.content)
    }

    @Test
    fun scenarioParamsAreJsonObjects() {
        SCENARIOS.forEach { assertTrue(it.paramJson, Json.parseToJsonElement(it.paramJson) is JsonObject) }
    }

    @Test
    fun koreanReplyForFind() {
        val data = """[{"rank":1,"name":"팔당 노을"},{"rank":2,"name":"변산 노을"},{"rank":3,"name":"애월 노을"}]"""
        val json = """{"functionName":"FIND_DRIVING_COURSES","status":"success","message":"Found 5 courses.","data":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(data))}}"""
        val text = koreanReply(json)
        assertTrue(text, "3곳" in text)
        listOf("팔당 노을", "변산 노을", "애월 노을").forEach { assertTrue(text, it in text) }
    }

    @Test
    fun koreanReplyForFail() {
        val text = koreanReply("""{"functionName":"START_DRIVING_COURSE","status":"fail","message":"No course is running right now."}""")
        assertTrue(text, text.startsWith("할 수 없어요"))
        assertTrue(text, "No course is running right now." in text)
    }
}
