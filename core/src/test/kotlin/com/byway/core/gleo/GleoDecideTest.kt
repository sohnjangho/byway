package com.byway.core.gleo

import com.byway.core.course.Course
import com.byway.core.course.CourseTags
import com.byway.core.course.Section
import com.byway.core.course.Spot
import com.byway.core.drive.Prompt
import com.byway.core.geo.GeoPoint
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoDecideTest {
    private val base = GeoPoint(37.5, 127.0)

    private fun course(id: String, name: String = id, spots: List<Spot> = emptyList(), tags: CourseTags = CourseTags()): Course {
        val path = (0..60).map { GeoPoint(base.lat + 0.001 * it, base.lon) }
        val sections = listOf(Section("고갯길", 0, 20), Section("강변", 20, 40), Section("호숫가", 40, 60))
        return Course(id, name, path, sections, spots = spots, tags = tags, region = "수도권")
    }

    private val paldang = course("paldang", "팔당 노을", tags = CourseTags(times = listOf("sunset")))
    private val byeonsan = course("byeonsan", "변산 노을", tags = CourseTags(times = listOf("sunset")))
    private val aewol = course("aewol", "애월 노을", tags = CourseTags(times = listOf("sunset")))
    private val forest = course("forest", "숲길")

    private fun world(
        courses: List<Course> = listOf(paldang, byeonsan, aewol, forest),
        here: GeoPoint? = null,
        running: Course? = null,
        sectionIndex: Int = 0,
        remainingMeters: Double? = null,
        prompt: Prompt? = null,
        recording: Boolean = false,
        partyCode: String? = null,
        savedRun: Pair<String, Int>? = null,
        lastResults: List<String> = emptyList(),
    ) = GleoWorld(
        courses, here, emptySet(), running, sectionIndex, remainingMeters, prompt, recording, partyCode, savedRun, lastResults,
        metersOf = { 50_300.0 }, wordsOf = { emptyList() }, moodNames = { listOf("노을") },
    )

    private fun p(vararg pairs: Pair<String, Any>): Map<String, JsonPrimitive> = pairs.associate { (k, v) ->
        k to when (v) {
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            else -> error("type")
        }
    }

    private fun control(command: String, world: GleoWorld) = decideGleo(GleoNames.CONTROL, p("command" to command), world)

    @Test
    fun findEmptyAsks() {
        assertEquals(GleoStatus.ASKING, decideGleo(GleoNames.FIND, emptyMap(), world()).reply.status)
    }

    @Test
    fun findRemembersTopIds() {
        val out = decideGleo(GleoNames.FIND, p("mood" to "sunset"), world())
        assertEquals(GleoStatus.SUCCESS, out.reply.status)
        assertEquals(GleoEffect.Remember(listOf("paldang", "byeonsan", "aewol")), out.effect)
        assertEquals(3, out.reply.data.size)
        assertEquals("팔당 노을", out.reply.data[0]["name"])
        assertEquals(1, out.reply.data[0]["rank"])
        assertEquals(50.3, out.reply.data[0]["km"])
        assertEquals(listOf("노을"), out.reply.data[0]["moods"])
    }

    @Test
    fun findNothingIsSuccess() {
        val out = decideGleo(GleoNames.FIND, p("searchTerm" to "없는말"), world())
        assertEquals(GleoStatus.SUCCESS, out.reply.status)
        assertTrue(out.reply.data.isEmpty())
    }

    @Test
    fun startByNameWhileMovingStarts() {
        val out = decideGleo(GleoNames.START, p("courseName" to "팔당 노을"), world())
        assertEquals(GleoEffect.Start("paldang", 0), out.effect)
        assertEquals(GleoStatus.SUCCESS, out.reply.status)
        assertEquals(GleoMode.SILENT, out.reply.mode)
        assertEquals(3, out.reply.data[0]["sections"])
        assertEquals("고갯길", out.reply.data[0]["firstSection"])
    }

    @Test
    fun secondStartWhileRunningFails() {
        val out = decideGleo(GleoNames.START, p("courseName" to "변산 노을"), world(running = paldang))
        assertEquals(GleoStatus.FAIL, out.reply.status)
        assertEquals(GleoEffect.None, out.effect)
    }

    @Test
    fun startAmbiguousAsks() {
        val out = decideGleo(GleoNames.START, p("courseName" to "노을"), world())
        assertEquals(GleoStatus.ASKING, out.reply.status)
        assertEquals(GleoEffect.None, out.effect)
        assertTrue(out.reply.data.size in 2..3)
    }

    @Test
    fun startByRankUsesLastResults() {
        assertEquals(GleoEffect.Start("byeonsan", 0), decideGleo(GleoNames.START, p("resultRank" to 2), world(lastResults = listOf("paldang", "byeonsan"))).effect)
    }

    @Test
    fun startRankWithoutResultsAsks() {
        val out = decideGleo(GleoNames.START, p("resultRank" to 1), world())
        assertEquals(GleoStatus.ASKING, out.reply.status)
        assertEquals(GleoEffect.None, out.effect)
        assertEquals(GleoStatus.ASKING, decideGleo(GleoNames.START, emptyMap(), world()).reply.status)
    }

    @Test
    fun resumeLastRunStartsFromProgress() {
        assertEquals(GleoEffect.Start("forest", 40), decideGleo(GleoNames.START, p("resumeLastRun" to true), world(savedRun = "forest" to 40)).effect)
    }

    @Test
    fun resumeWithoutSavedRunFails() {
        assertEquals(GleoStatus.FAIL, decideGleo(GleoNames.START, p("resumeLastRun" to true), world()).reply.status)
    }

    /** 설명이 영어라 이름이 영어로 옮겨져 올 수 있다. 그때는 직전 결과를 다시 보이며 되묻는다(설계 6절). */
    @Test
    fun nameNotFoundFallsBackToLastResults() {
        val out = decideGleo(GleoNames.START, p("courseName" to "Paldang Sunset"), world(lastResults = listOf("paldang", "byeonsan")))
        assertEquals(GleoStatus.ASKING, out.reply.status)
        assertEquals(listOf("팔당 노을", "변산 노을"), out.reply.data.map { it["name"] })
        assertEquals(GleoStatus.FAIL, decideGleo(GleoNames.START, p("courseName" to "Paldang Sunset"), world()).reply.status)
    }

    @Test
    fun stopByAnswersDetour() {
        assertEquals(GleoEffect.Answer(true), control("stop_by", world(running = paldang, prompt = Prompt.DETOUR)).effect)
        assertEquals(GleoEffect.Answer(false), control("back_to_course", world(running = paldang, prompt = Prompt.DETOUR)).effect)
    }

    @Test
    fun stopByWithoutPromptFails() {
        val out = control("stop_by", world(running = paldang))
        assertEquals(GleoStatus.FAIL, out.reply.status)
        assertEquals(GleoEffect.None, out.effect)
    }

    @Test
    fun resumeCommandNeedsResumePrompt() {
        assertEquals(GleoEffect.Answer(true), control("resume", world(running = paldang, prompt = Prompt.RESUME)).effect)
        assertEquals(GleoStatus.FAIL, control("resume", world(running = paldang, prompt = Prompt.DETOUR)).reply.status)
    }

    @Test
    fun controlNeedsRunningCourse() {
        listOf("status", "next_spot", "end_course", "stop_by").forEach {
            val out = control(it, world())
            assertEquals(it, GleoStatus.FAIL, out.reply.status)
            assertEquals(it, GleoEffect.None, out.effect)
        }
        assertEquals(GleoEffect.EndCourse, control("end_course", world(running = paldang)).effect)
    }

    @Test
    fun statusReportsSection() {
        val out = control("status", world(running = paldang, sectionIndex = 1, remainingMeters = 12_440.0))
        assertEquals(GleoStatus.SUCCESS, out.reply.status)
        val data = out.reply.data.single()
        assertEquals("팔당 노을", data["course"])
        assertEquals(12.4, data["remainingKm"])
        assertEquals("2/3", data["section"])
        assertEquals("강변", data["sectionName"])
        assertEquals("호숫가", data["nextSectionName"])
    }

    @Test
    fun nextSpotIsFirstAhead() {
        val spots = listOf(5, 20, 40).map { Spot("곳$it", "경치", GeoPoint(base.lat + 0.001 * it, base.lon), it) }
        val withSpots = course("s", spots = spots)
        val here = GeoPoint(base.lat + 0.001 * 25, base.lon)
        assertEquals("곳40", nextSpot(withSpots, here, 0)?.name)
        // 위치를 모르면 지금 구간의 시작점이 기준이다.
        assertEquals("곳5", nextSpot(withSpots, null, 0)?.name)
        assertEquals("곳40", nextSpot(withSpots, null, 1)?.name)
        assertEquals(null, nextSpot(withSpots, GeoPoint(base.lat + 0.001 * 50, base.lon), 2))
        val out = control("next_spot", world(running = withSpots, here = here))
        assertEquals("곳40", out.reply.data.single()["name"])
    }

    @Test
    fun settingsWorkWithoutCourse() {
        assertEquals(GleoEffect.SetVoice(false), control("voice_guide_off", world()).effect)
        assertEquals(GleoEffect.SetVoice(true), control("voice_guide_on", world()).effect)
        val cabin = control("cabin_effects_off", world())
        assertEquals(GleoEffect.SetCabin(false), cabin.effect)
        assertTrue(cabin.reply.message, "next course" in cabin.reply.message)
    }

    @Test
    fun stopRecordingNeedsRecording() {
        assertEquals(GleoStatus.FAIL, control("stop_recording", world()).reply.status)
        assertEquals(GleoEffect.StopRecording, control("stop_recording", world(recording = true)).effect)
    }

    @Test
    fun stopSharingNeedsParty() {
        assertEquals(GleoStatus.FAIL, control("stop_location_sharing", world()).reply.status)
        assertEquals(GleoEffect.StopSharing("D7FUZY"), control("stop_location_sharing", world(partyCode = "D7FUZY")).effect)
    }

    @Test
    fun unknownCommandFails() {
        assertEquals(GleoStatus.FAIL, control("fly", world(running = paldang)).reply.status)
        assertEquals(GleoStatus.ASKING, decideGleo(GleoNames.CONTROL, emptyMap(), world()).reply.status)
        assertEquals(GleoStatus.FAIL, decideGleo("OTHER", emptyMap(), world()).reply.status)
    }
}
