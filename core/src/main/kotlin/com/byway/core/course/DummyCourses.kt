package com.byway.core.course

import com.byway.core.geo.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil

/** 더미 코스의 지점 사이를 이을 때 점 간격의 최댓값(m). 엔진의 진행률이 매끄럽게 움직일 만큼 촘촘하다. */
const val DUMMY_STEP_METERS = 200.0

@Serializable
private data class DummyFile(
    val id: String,
    val name: String,
    val region: String,
    val summary: String = "",
    val km: Double,
    val points: List<DummyPoint>,
    val tags: DummyTags = DummyTags(),
)

@Serializable
private data class DummyPoint(val name: String, val lat: Double, val lon: Double)

@Serializable
private data class DummyTags(
    val themes: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val times: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    val weathers: List<String> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * 지도 없는 더미 코스 목록을 읽는다(더 많은 코스 설계 4절). 지점을 직선으로 이어 [DUMMY_STEP_METERS] 간격의 path 를 만들고,
 * 지점과 지점 사이마다 구간 하나를 두어 그 다리의 도착 지명을 구간 이름으로 쓴다.
 */
fun parseDummyCourses(text: String): List<Course> = json.decodeFromString<List<DummyFile>>(text).map { file ->
    require(file.points.size >= 2) { "지점이 모자란 더미 코스: ${file.id}" }
    val points = file.points.map { GeoPoint(it.lat, it.lon) }
    val path = mutableListOf(points.first())
    val sections = mutableListOf<Section>()
    points.zipWithNext().forEachIndexed { leg, (from, to) ->
        val start = if (leg == 0) 0 else path.size
        val steps = maxOf(1, ceil(from.distanceTo(to) / DUMMY_STEP_METERS).toInt())
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            path += if (i == steps) to else GeoPoint(from.lat + (to.lat - from.lat) * t, from.lon + (to.lon - from.lon) * t)
        }
        sections += Section(file.points[leg + 1].name, start, path.lastIndex)
    }
    Course(
        id = file.id,
        name = file.name,
        path = path,
        sections = sections,
        tags = file.tags.let { CourseTags(themes = it.themes, moods = it.moods, times = it.times, seasons = it.seasons, weathers = it.weathers) },
        summary = file.summary,
        region = file.region,
        dummy = true,
        declaredMeters = file.km * 1_000,
    )
}

/** 더미 코스는 직선 path 길이를 표기 거리로 늘려 보인다. 실제 코스는 1이다. */
fun Course.lengthScale(): Double {
    val declared = declaredMeters ?: return 1.0
    val line = path.zipWithNext { a, b -> a.distanceTo(b) }.sum()
    return if (line > 0) declared / line else 1.0
}
