package com.byway.core.gleo

import com.byway.core.course.LengthBand
import com.byway.core.course.RankBy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Gleo Interface 규격(Gleo 음성 연동 설계 2절). Gleo 가 함수 이름을 Intent action 으로, 인자를 extra [PARAM_EXTRA]
 * 의 JSON 으로 보낸다. 앱은 [GleoReply] 를 Callback 방송으로 돌려준다.
 */
object GleoNames {
    const val FIND = "FIND_DRIVING_COURSES"
    const val START = "START_DRIVING_COURSE"
    const val CONTROL = "CONTROL_BYWAY_DRIVE"
    const val PARAM_EXTRA = "ACTION_PARAM"
    val ALL: List<String> = listOf(FIND, START, CONTROL)
}

/**
 * [raw]의 JSON 객체를 이름 → 값으로 읽는다. 문서 예시처럼 JSON 을 따옴표로 한 번 더 감싼 문자열도 받는다.
 * 없거나 깨졌거나 객체가 아니면 빈 맵이다. 판단 쪽이 빠진 인자를 되묻는다.
 */
fun parseGleoParams(raw: String?): Map<String, JsonPrimitive> {
    if (raw.isNullOrBlank()) return emptyMap()
    val outer = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        ?: docLiteral(raw)?.let { inner -> runCatching { Json.parseToJsonElement(inner) }.getOrNull() }
        ?: return emptyMap()
    val inner = if (outer is JsonPrimitive && outer.isString) runCatching { Json.parseToJsonElement(outer.content) }.getOrNull() else outer
    val obj = inner as? JsonObject ?: return emptyMap()
    return obj.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { key to it } }.toMap()
}

/**
 * 문서 Kotlin 예시(`"\"{\"title\":...}\""`)가 실제로 만드는 값은 바깥만 따옴표로 감싸고 안쪽은 이스케이프하지 않은
 * 문자열이다. 그 바깥 따옴표를 벗긴다(검토 I3).
 */
private fun docLiteral(raw: String): String? = raw.trim().takeIf { it.length > 2 && it.startsWith("\"{") && it.endsWith("}\"") }?.let { it.substring(1, it.length - 1) }

/**
 * Gleo 인자. [raw](`ACTION_PARAM`)를 먼저 읽고, 없거나 비면 Intent extras 의 글자·숫자·참거짓 값으로 읽는다.
 * 문서 본문은 값마다 putExtra 한다고도 쓰기 때문이다(검토 I3).
 */
fun gleoParamsFrom(raw: String?, extras: Map<String, Any?>): Map<String, JsonPrimitive> =
    parseGleoParams(raw).ifEmpty {
        extras.mapNotNull { (key, value) ->
            when (value) {
                is String -> key to JsonPrimitive(value)
                is Number -> key to JsonPrimitive(value)
                is Boolean -> key to JsonPrimitive(value)
                else -> null
            }
        }.toMap()
    }

enum class GleoStatus { SUCCESS, ASKING, FAIL }

/** Gleo 가 답을 전하는 방법. [SILENT]는 앱이 스스로 말할 때 쓴다. */
enum class GleoMode { VOICE, BEEP, SILENT }

/** Callback 하나. [message]는 영어 한 문장이고 고유명은 한국어 그대로다. [data]는 Gleo 가 답을 만들 때 쓰는 값이다. */
data class GleoReply(
    val function: String,
    val status: GleoStatus,
    val message: String,
    val data: List<Map<String, Any>> = emptyList(),
    val mode: GleoMode = GleoMode.VOICE,
)

/** Callback Schema: data 는 JSON 배열을 담은 문자열이다. mode 는 voice(기본)면 뺀다. */
fun GleoReply.toJson(): String {
    val fields = linkedMapOf<String, JsonElement>(
        "functionName" to JsonPrimitive(function),
        "status" to JsonPrimitive(status.name.lowercase()),
        "message" to JsonPrimitive(message),
    )
    if (data.isNotEmpty()) fields["data"] = JsonPrimitive(JsonArray(data.map { item -> JsonObject(item.mapValues { toElement(it.value) }) }).toString())
    if (mode != GleoMode.VOICE) fields["mode"] = JsonPrimitive(mode.name.lowercase())
    return JsonObject(fields).toString()
}

private fun toElement(value: Any): JsonElement = when (value) {
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.filterNotNull().map(::toElement))
    else -> JsonPrimitive(value.toString())
}

/** 스키마의 무드 enum(영어) → [com.byway.core.course.MOOD_KEYS]. */
val MOOD_ENUM: Map<String, String> = linkedMapOf(
    "alone" to "moods:alone", "date" to "moods:date", "family" to "moods:family",
    "spring" to "seasons:spring", "summer" to "seasons:summer", "autumn" to "seasons:autumn", "winter" to "seasons:winter",
    "rain" to "weathers:rain",
    "sunrise" to "times:sunrise", "day" to "times:day", "sunset" to "times:sunset", "night" to "times:night",
)

val LENGTH_ENUM: Map<String, LengthBand> = linkedMapOf(
    "up_to_30km" to LengthBand.UP_TO_30, "up_to_60km" to LengthBand.UP_TO_60,
    "up_to_100km" to LengthBand.UP_TO_100, "over_100km" to LengthBand.OVER_100,
)

val RANK_ENUM: Map<String, RankBy> = linkedMapOf(
    "curves" to RankBy.CURVES, "driving_fun" to RankBy.FUN, "scenery" to RankBy.SCENERY, "length" to RankBy.LENGTH,
)

/** [GleoNames.CONTROL]의 command 값(설계 3-3). */
val COMMANDS: List<String> = listOf(
    "stop_by", "back_to_course", "resume", "end_course", "status", "next_spot",
    "voice_guide_on", "voice_guide_off", "cabin_effects_on", "cabin_effects_off",
    "stop_recording", "stop_location_sharing",
)
