package com.byway.gleosim

/**
 * 미리 정한 한국어 말 → Gleo 가 보낼 함수 호출. 실제 Gleo 의 말 이해(LLM)를 이 표가 대신한다(설계 9절).
 * 함수 이름과 인자 모양은 에움길 assets/schema.json 과 같다.
 */
data class Scenario(val utterance: String, val function: String, val paramJson: String)

private const val FIND = "FIND_DRIVING_COURSES"
private const val START = "START_DRIVING_COURSE"
private const val CONTROL = "CONTROL_BYWAY_DRIVE"

private fun command(name: String) = """{"command":"$name"}"""

/** 에움길 스키마의 component. 출발만 activity(startActivity)이고, 나머지는 service(sendBroadcast)다. */
fun byActivity(function: String): Boolean = function == START

val SCENARIOS: List<Scenario> = listOf(
    Scenario("노을 보기 좋은 코스 찾아줘", FIND, """{"mood":"sunset"}"""),
    Scenario("팔당 노을로 출발해줘", START, """{"courseName":"팔당 노을"}"""),
    Scenario("잠깐 들를게", CONTROL, command("stop_by")),
    Scenario("코스로 돌아갈래", CONTROL, command("back_to_course")),
    Scenario("얼마나 남았어?", CONTROL, command("status")),
    Scenario("다음 볼거리 뭐야?", CONTROL, command("next_spot")),
    Scenario("음성 안내 꺼줘", CONTROL, command("voice_guide_off")),
    Scenario("코스 그만할래", CONTROL, command("end_course")),
)
