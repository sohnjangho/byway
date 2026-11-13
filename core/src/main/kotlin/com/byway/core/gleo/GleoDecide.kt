package com.byway.core.gleo

import com.byway.core.course.Course
import com.byway.core.course.Spot
import com.byway.core.drive.Prompt
import com.byway.core.geo.GeoPoint
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong

/**
 * Gleo 판단에 필요한 지금 상태. 앱이 요청마다 만들어 넘긴다.
 * [savedRun]은 달리다 만 코스의 id 와 지점 번호, [lastResults]는 10분 안의 직전 찾기 결과 id 다.
 */
data class GleoWorld(
    val courses: List<Course>,
    val here: GeoPoint?,
    val mine: Set<String>,
    val running: Course?,
    val sectionIndex: Int,
    val remainingMeters: Double?,
    val prompt: Prompt?,
    val recording: Boolean,
    val partyCode: String?,
    val savedRun: Pair<String, Int>?,
    val lastResults: List<String>,
    /** 위치 권한. 화면 출발은 권한부터 묻지만 음성은 물을 수 없다(검토 I2). */
    val locationGranted: Boolean,
    val metersOf: (Course) -> Double,
    val wordsOf: (Course) -> List<String>,
    val moodNames: (Course) -> List<String>,
)

/** 판단이 앱에 시키는 일. 실행은 app 이 맡는다. */
sealed interface GleoEffect {
    data object None : GleoEffect
    data class Remember(val ids: List<String>) : GleoEffect
    data class Start(val courseId: String, val resumeFrom: Int) : GleoEffect
    data class Answer(val yes: Boolean) : GleoEffect
    data object EndCourse : GleoEffect
    data class SetVoice(val on: Boolean) : GleoEffect
    data class SetCabin(val on: Boolean) : GleoEffect
    data object StopRecording : GleoEffect
    data class StopSharing(val code: String) : GleoEffect
}

data class GleoOutcome(val reply: GleoReply, val effect: GleoEffect = GleoEffect.None)

/** 함수 하나를 판단한다(설계 3절). 화면의 같은 동작과 같은 조건을 쓰고, 음성 출발만 주행 중에도 허용한다(PM 11-13). */
fun decideGleo(function: String, params: Map<String, JsonPrimitive>, world: GleoWorld): GleoOutcome = when (function) {
    GleoNames.FIND -> find(params, world)
    GleoNames.START -> start(params, world)
    GleoNames.CONTROL -> control(params, world)
    else -> GleoOutcome(GleoReply(function, GleoStatus.FAIL, "This function is not supported."))
}

private fun Map<String, JsonPrimitive>.text(key: String): String = this[key]?.contentOrNull?.trim().orEmpty()
private fun Map<String, JsonPrimitive>.number(key: String): Double? = this[key]?.doubleOrNull
private fun Map<String, JsonPrimitive>.flag(key: String): Boolean = this[key]?.booleanOrNull ?: false

private fun km(meters: Double): Double = (meters / 100.0).roundToLong() / 10.0

/** 차와 출발점의 거리는 5km 단위로만 알린다. 다른 앱도 Callback 을 받을 수 있어 차 위치를 좁히지 못하게 한다(검토 I5). */
private fun roughKm(meters: Double): Double = maxOf(5L, (meters / 5_000.0).roundToLong() * 5).toDouble()

private fun find(params: Map<String, JsonPrimitive>, world: GleoWorld): GleoOutcome {
    val query = VoiceQuery(
        searchTerm = params.text("searchTerm"),
        mood = params.text("mood").ifEmpty { null },
        nearbyKm = params.number("nearbyKm"),
        length = params.text("lengthBand").ifEmpty { null },
        region = params.text("region").ifEmpty { null },
        rankBy = params.text("rankBy").ifEmpty { null },
        myCoursesOnly = params.flag("myCoursesOnly"),
    )
    if (query.isEmpty()) return GleoOutcome(GleoReply(GleoNames.FIND, GleoStatus.ASKING, "A search term, mood, distance or region is needed to find courses."))
    val hits = findForVoice(world.courses, query, world.here, world.mine, world.metersOf, world.wordsOf)
    val data = hits.top.mapIndexed { i, found ->
        buildMap<String, Any> {
            put("rank", i + 1)
            put("name", found.course.name)
            put("region", found.course.region)
            put("km", km(world.metersOf(found.course)))
            found.awayMeters?.let { put("awayKm", roughKm(it)) }
            put("moods", world.moodNames(found.course))
            put("dummy", found.course.dummy)
        }
    }
    val unknown = if (hits.positionUnknown) " The current position is unknown, so the distance condition was skipped." else ""
    val message = if (hits.total == 0) {
        "The search was completed, but no matching courses were found.$unknown"
    } else {
        "Found ${hits.total} courses; the top ${hits.top.size} are ${hits.top.joinToString(", ") { it.course.name }}.$unknown"
    }
    return GleoOutcome(GleoReply(GleoNames.FIND, GleoStatus.SUCCESS, message, data), GleoEffect.Remember(hits.top.map { it.course.id }))
}

private fun start(params: Map<String, JsonPrimitive>, world: GleoWorld): GleoOutcome {
    world.running?.let {
        return GleoOutcome(GleoReply(GleoNames.START, GleoStatus.FAIL, "The course ${it.name} is already running; end it before starting another."))
    }
    // 화면과 같은 조건: 기록 중에는 출발 버튼이 없고, 위치 권한이 없으면 먼저 묻는다(검토 I2).
    if (world.recording) return GleoOutcome(GleoReply(GleoNames.START, GleoStatus.FAIL, "A drive is being recorded; stop recording before starting a course."))
    if (!world.locationGranted) return GleoOutcome(GleoReply(GleoNames.START, GleoStatus.FAIL, "Open 에움길 and allow location access first."))
    if (params.flag("resumeLastRun")) {
        val saved = world.savedRun?.let { (id, from) -> world.courses.firstOrNull { it.id == id }?.let { it to from } }
            ?: return GleoOutcome(GleoReply(GleoNames.START, GleoStatus.FAIL, "There is no paused course to resume."))
        return started(saved.first, saved.second, world)
    }
    val name = params.text("courseName")
    val rank = params.number("resultRank")?.toInt()
    if (name.isEmpty() && rank == null) return GleoOutcome(GleoReply(GleoNames.START, GleoStatus.ASKING, "The name of the course to start is needed."))
    val match = if (name.isEmpty()) NameMatch.None else matchCourse(world.courses, name, world.wordsOf)
    if (match is NameMatch.One) return started(match.course, 0, world)
    val recent = world.lastResults.mapNotNull { id -> world.courses.firstOrNull { it.id == id } }
    if (rank != null) {
        recent.getOrNull(rank - 1)?.let { return started(it, 0, world) }
        if (recent.isEmpty() && match !is NameMatch.Many) {
            return GleoOutcome(GleoReply(GleoNames.START, GleoStatus.ASKING, "There are no recent search results to choose from; the course name is needed."))
        }
    }
    return when {
        match is NameMatch.Many -> asking("Several courses match $name; one of them must be chosen.", match.candidates, world)
        recent.isNotEmpty() -> asking("No course named $name was found; one of the recent results can be chosen.", recent, world)
        else -> GleoOutcome(GleoReply(GleoNames.START, GleoStatus.FAIL, "No course named $name was found."))
    }
}

/** 되물은 후보를 기억해 "두 번째 거"(resultRank)가 보여 준 후보를 고르게 한다(검토 I1). */
private fun asking(message: String, courses: List<Course>, world: GleoWorld): GleoOutcome {
    val shown = courses.take(VOICE_TOP)
    return GleoOutcome(
        GleoReply(GleoNames.START, GleoStatus.ASKING, message, shown.mapIndexed { i, c ->
            mapOf("rank" to i + 1, "name" to c.name, "region" to c.region, "km" to km(world.metersOf(c)))
        }),
        GleoEffect.Remember(shown.map { it.id }),
    )
}

/** 출발은 에움길이 스스로 안내 음성을 내므로 Gleo 는 말하지 않는다(silent, 설계 3-2). */
private fun started(course: Course, from: Int, world: GleoWorld) = GleoOutcome(
    GleoReply(
        GleoNames.START, GleoStatus.SUCCESS, "Started the course ${course.name}; the route is being set in navigation.",
        listOf(
            buildMap {
                put("name", course.name)
                put("km", km(world.metersOf(course)))
                put("sections", course.sections.size)
                course.sections.firstOrNull()?.let { put("firstSection", it.name) }
            },
        ),
        GleoMode.SILENT,
    ),
    GleoEffect.Start(course.id, from),
)

private val NEEDS_COURSE = setOf("stop_by", "back_to_course", "resume", "end_course", "status", "next_spot")

private fun control(params: Map<String, JsonPrimitive>, world: GleoWorld): GleoOutcome {
    fun reply(status: GleoStatus, message: String, data: List<Map<String, Any>> = emptyList()) = GleoReply(GleoNames.CONTROL, status, message, data)
    fun fail(message: String) = GleoOutcome(reply(GleoStatus.FAIL, message))
    fun done(message: String, effect: GleoEffect) = GleoOutcome(reply(GleoStatus.SUCCESS, message), effect)

    val command = params.text("command")
    if (command.isEmpty()) return GleoOutcome(reply(GleoStatus.ASKING, "The command to run is needed."))
    if (command !in COMMANDS) return fail("The command $command is not supported.")
    val running = world.running
    if (command in NEEDS_COURSE && running == null) return fail("No course is running right now.")
    return when (command) {
        "stop_by" -> if (world.prompt == Prompt.DETOUR) done("Okay, the course will wait while you stop by.", GleoEffect.Answer(true)) else fail("There is no detour question to answer right now.")
        "back_to_course" -> if (world.prompt == Prompt.DETOUR) done("Guiding the way back to the course.", GleoEffect.Answer(false)) else fail("There is no detour question to answer right now.")
        "resume" -> if (world.prompt == Prompt.RESUME) done("Continuing the course.", GleoEffect.Answer(true)) else fail("The course is not waiting to be resumed right now.")
        "end_course" -> done("The course ${running!!.name} has ended.", GleoEffect.EndCourse)
        "status" -> GleoOutcome(reply(GleoStatus.SUCCESS, "Here is the progress of the course ${running!!.name}.", listOf(status(running, world))))
        "next_spot" -> nextSpot(running!!, world.here, world.sectionIndex)?.let { spot ->
            val from = pointIndex(running, world.here, world.sectionIndex)
            GleoOutcome(reply(GleoStatus.SUCCESS, "The next spot is ${spot.name}.", listOf(mapOf("name" to spot.name, "kind" to spot.kind, "km" to km(along(running, from, spot.pathIndex))))))
        } ?: GleoOutcome(reply(GleoStatus.SUCCESS, "There are no more spots ahead on this course."))
        "voice_guide_on" -> done("Voice guidance is now on.", GleoEffect.SetVoice(true))
        "voice_guide_off" -> done("Voice guidance is now off.", GleoEffect.SetVoice(false))
        // 앱은 이 설정을 코스를 시작할 때 읽는다. 화면 설정과 같게 다음 코스부터 적용된다(설계 3-3).
        "cabin_effects_on" -> done("Cabin effects will be on from the next course.", GleoEffect.SetCabin(true))
        "cabin_effects_off" -> done("Cabin effects will be off from the next course.", GleoEffect.SetCabin(false))
        "stop_recording" -> if (world.recording) done("Recording stopped; it can be saved on the parked screen.", GleoEffect.StopRecording) else fail("No drive is being recorded.")
        else -> world.partyCode?.let { done("Location sharing is being turned off.", GleoEffect.StopSharing(it)) } ?: fail("There is no group drive right now.")
    }
}

private fun status(course: Course, world: GleoWorld): Map<String, Any> = buildMap {
    put("course", course.name)
    world.remainingMeters?.let { put("remainingKm", km(it)) }
    put("section", "${world.sectionIndex + 1}/${course.sections.size}")
    course.sections.getOrNull(world.sectionIndex)?.let { put("sectionName", it.name) }
    course.sections.getOrNull(world.sectionIndex + 1)?.let { put("nextSectionName", it.name) }
}

/** 지금 자리보다 앞에 있는 코스 주변 지점 중 첫 번째. 자리는 [here]에 가장 가까운 경로 점, 모르면 지금 구간의 시작점이다. */
fun nextSpot(course: Course, here: GeoPoint?, sectionIndex: Int): Spot? {
    val from = pointIndex(course, here, sectionIndex)
    return course.spots.filter { it.pathIndex > from }.minByOrNull { it.pathIndex }
}

private fun pointIndex(course: Course, here: GeoPoint?, sectionIndex: Int): Int =
    here?.let { point -> course.path.indices.minByOrNull { course.path[it].distanceTo(point) } }
        ?: course.sections.getOrNull(sectionIndex)?.startIndex ?: 0

private fun along(course: Course, from: Int, to: Int): Double =
    (from until to.coerceAtMost(course.path.lastIndex)).sumOf { course.path[it].distanceTo(course.path[it + 1]) }
