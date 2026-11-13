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
            } ?: controlReply(text("message"))
        }
    }
}

/** 조작 명령의 영어 답(에움길 Callback message)을 한국어 말풍선으로 옮긴다. 모르는 문장은 "알겠어요"다. */
private fun controlReply(message: String): String = when {
    message.startsWith("Okay, the course will wait") -> "다녀오세요. 코스는 기다리고 있을게요"
    message.startsWith("Guiding the way back") -> "코스로 돌아가는 길을 안내할게요"
    message.startsWith("Continuing the course") -> "코스를 이어서 안내할게요"
    message.endsWith("has ended.") -> "코스를 끝냈어요"
    message == "Voice guidance is now off." -> "음성 안내를 껐어요"
    message == "Voice guidance is now on." -> "음성 안내를 켰어요"
    message.startsWith("Cabin effects will be off") -> "차 안 연출은 다음 코스부터 꺼 둘게요"
    message.startsWith("Cabin effects will be on") -> "차 안 연출은 다음 코스부터 켤게요"
    message.startsWith("Recording stopped") -> "기록을 멈췄어요. 정차하면 저장할 수 있어요"
    message.startsWith("Location sharing") -> "위치 공유를 껐어요"
    else -> "알겠어요"
}

/** Gleo 패널 목록 한 줄(디자인 가이드 List Type): 번호, 제목, 설명 줄, 회색 태그. 카드 하나면 번호가 없다. */
data class PanelItem(val number: Int?, val title: String, val meta: String, val tags: List<String>)

data class Panel(val items: List<PanelItem>)

/** Callback 의 data 로 Gleo 패널 내용을 만든다. 보여 줄 data 가 없으면 말풍선만 쓴다. */
fun panelFor(responseJson: String): Panel? {
    val response = runCatching { Json.parseToJsonElement(responseJson).jsonObject }.getOrNull() ?: return null
    fun text(key: String) = (response[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    if (text("status") == "fail") return null
    val data = runCatching { Json.parseToJsonElement(text("data")).jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())
    if (data.isEmpty()) return null
    fun JsonObject.value(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    fun JsonObject.list(key: String) = runCatching { this[key]!!.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }.getOrDefault(emptyList())
    fun km(value: String?) = value?.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) "${it.toInt()}" else "$it" }
    val items = data.mapNotNull { item ->
        when {
            item.value("course") != null -> PanelItem(
                null, item.value("course")!!,
                listOfNotNull(km(item.value("remainingKm"))?.let { "남은 거리 ${it}km" }, item.value("section")?.let { "구간 $it" }).joinToString(" · "),
                listOfNotNull(item.value("sectionName")),
            )
            item.value("rank") != null -> PanelItem(
                item.value("rank")!!.toDoubleOrNull()?.toInt(), item.value("name").orEmpty(),
                listOfNotNull(km(item.value("km"))?.let { "${it}km" }, item.value("region")?.takeIf { it.isNotEmpty() }, km(item.value("awayKm"))?.let { "${it}km 거리" }).joinToString(" · "),
                item.list("moods").take(3),
            )
            item.value("sections") != null -> PanelItem(
                null, item.value("name").orEmpty(),
                listOfNotNull(km(item.value("km"))?.let { "${it}km" }, "구간 ${item.value("sections")}개").joinToString(" · "),
                listOfNotNull(item.value("firstSection")),
            )
            item.value("kind") != null -> PanelItem(null, item.value("name").orEmpty(), km(item.value("km"))?.let { "${it}km 앞" }.orEmpty(), listOfNotNull(item.value("kind")))
            else -> null
        }
    }
    return items.takeIf { it.isNotEmpty() }?.let(::Panel)
}
