package com.byway.gleosim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 에움길 Callback 을 한국어 답 말풍선으로 바꾼다. 실제 Gleo 는 LLM 이 답을 새로 만들지만 흉내 앱은 틀을 채운다.
 * 실패 문장은 에움길이 보낸 영어 message 를 그대로 붙여 무엇이 오갔는지 보인다.
 */
fun koreanReply(responseJson: String): String {
    val response = runCatching { Json.parseToJsonElement(responseJson).jsonObject }.getOrNull() ?: return "답을 읽지 못했어요"
    fun text(key: String) = (response[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val data = runCatching { Json.parseToJsonElement(text("data")).jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())
    fun JsonObject.value(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    val names = data.mapNotNull { it.value("name") }
    return when (text("status")) {
        "fail" -> "할 수 없어요: ${text("message")}"
        "asking" -> if (names.isNotEmpty()) "어느 코스로 할까요? " + names.mapIndexed { i, n -> "${i + 1}번 $n" }.joinToString(", ") else "조금 더 알려 주세요: ${text("message")}"
        else -> when (text("functionName")) {
            "FIND_DRIVING_COURSES" -> if (names.isEmpty()) "맞는 코스를 찾지 못했어요" else "코스 ${names.size}곳을 찾았어요: ${names.joinToString(", ")}"
            "START_DRIVING_COURSE" -> names.firstOrNull()?.let { "$it 코스를 시작해요" } ?: "코스를 시작해요"
            else -> data.firstOrNull()?.let { item ->
                when {
                    item.value("remainingKm") != null || item.value("section") != null ->
                        "남은 거리는 ${item.value("remainingKm") ?: "-"}km, ${item.value("section")} 구간(${item.value("sectionName").orEmpty()})이에요"
                    item.value("name") != null -> "다음 볼거리는 ${item.value("name")}, ${item.value("km")}km 앞이에요"
                    else -> null
                }
            } ?: "알겠어요"
        }
    }
}
