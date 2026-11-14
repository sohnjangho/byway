package com.byway.service

import com.byway.core.gleo.COMMANDS
import com.byway.core.gleo.GleoNames
import com.byway.core.gleo.LENGTH_ENUM
import com.byway.core.gleo.MOOD_ENUM
import com.byway.core.gleo.RANK_ENUM
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gleo 가 읽는 assets/schema.json 이 코드의 이름·값과 매니페스트 action 과 어긋나지 않게 한다(설계 6절). */
class GleoSchemaTest {
    private val schema = Json.parseToJsonElement(File("src/main/assets/schema.json").readText()).jsonObject
    private val calls = schema.getValue("actionCalls").jsonArray.map { it.jsonObject.getValue("function").jsonObject }

    private fun enumOf(function: String, property: String): List<String> =
        calls.single { it.getValue("name").jsonPrimitive.content == function }
            .getValue("parameters").jsonObject.getValue("properties").jsonObject.getValue(property).jsonObject
            .getValue("enum").jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun schemaFunctionsMatchCodeAndManifest() {
        assertEquals("com.byway", schema.getValue("packageName").jsonPrimitive.content)
        assertTrue(calls.size <= 3)
        assertEquals(GleoNames.ALL.toSet(), calls.map { it.getValue("name").jsonPrimitive.content }.toSet())
        assertTrue(calls.all { it.getValue("component").jsonPrimitive.content == "service" })
        val manifest = File("src/main/AndroidManifest.xml").readText()
        GleoNames.ALL.forEach { assertTrue(it, Regex("""<action\s+android:name="$it"\s*/>""").containsMatchIn(manifest)) }
    }

    @Test
    fun schemaEnumsMatchCore() {
        assertEquals(MOOD_ENUM.keys.toList(), enumOf(GleoNames.FIND, "mood"))
        assertEquals(LENGTH_ENUM.keys.toList(), enumOf(GleoNames.FIND, "lengthBand"))
        assertEquals(RANK_ENUM.keys.toList(), enumOf(GleoNames.FIND, "rankBy"))
        assertEquals(listOf("강원", "경상", "남해안", "수도권", "전라", "제주", "충청"), enumOf(GleoNames.FIND, "region"))
        assertEquals(COMMANDS, enumOf(GleoNames.CONTROL, "command"))
    }

    /** 문서: 모든 description 은 영어로 쓴다. */
    @Test
    fun schemaDescriptionsAreEnglish() {
        val hangul = Regex("[가-힣]")
        fun walk(element: JsonElement): List<String> = when (element) {
            is JsonObject -> element.flatMap { (key, value) ->
                if ((key == "description" || key == "appDescription") && value is JsonPrimitive) listOf(value.content) else walk(value)
            }
            is kotlinx.serialization.json.JsonArray -> element.flatMap(::walk)
            else -> emptyList()
        }
        val descriptions = walk(schema)
        assertTrue(descriptions.size >= 3 + 10)
        descriptions.forEach { assertTrue(it, !hangul.containsMatchIn(it)) }
    }
}
