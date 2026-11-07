package com.byway.core.course

import com.byway.core.geo.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val SUPPORTED_VERSIONS = 1..2

@Serializable
private data class CourseFile(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val attribution: String,
    val path: List<List<Double>>,
    val sections: List<SectionFile>,
    val summary: String = "",
    val toleranceMeters: Double? = null,
    val tags: TagsFile = TagsFile(),
    val spots: List<SpotFile> = emptyList(),
    val region: String = "",
    val elevation: List<Double> = emptyList(),
)

@Serializable
private data class SectionFile(
    val name: String,
    val startIndex: Int,
    val endIndex: Int,
    val mood: String? = null,
)

@Serializable
private data class TagsFile(
    val themes: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val times: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    val ages: List<String> = emptyList(),
)

@Serializable
private data class SpotFile(
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val pathIndex: Int,
)

private val json = Json { ignoreUnknownKeys = true }

fun parseCourse(text: String): Course {
    val file = json.decodeFromString<CourseFile>(text)
    require(file.schemaVersion in SUPPORTED_VERSIONS) { "지원하지 않는 코스 형식: ${file.schemaVersion}" }
    require(file.schemaVersion < 2 || file.attribution.isNotBlank()) { "출처가 없는 코스: ${file.id}" }
    val sections = file.sections.map { Section(it.name, it.startIndex, it.endIndex, it.mood) }
    requireContinuous(sections, file.path.lastIndex)
    require(file.spots.all { it.pathIndex in file.path.indices }) { "코스 밖 스팟: ${file.id}" }
    return Course(
        id = file.id,
        name = file.name,
        path = file.path.map { (lat, lon) -> GeoPoint(lat, lon) },
        sections = sections,
        spots = file.spots.map { Spot(it.name, it.kind, GeoPoint(it.lat, it.lon), it.pathIndex) },
        tags = CourseTags(file.tags.themes, file.tags.moods, file.tags.times, file.tags.seasons, file.tags.ages),
        summary = file.summary,
        attribution = file.attribution,
        toleranceMeters = file.toleranceMeters,
        region = file.region,
        elevation = file.elevation,
    )
}

private fun requireContinuous(sections: List<Section>, lastIndex: Int) {
    require(sections.first().startIndex == 0 && sections.last().endIndex == lastIndex) { "구간이 코스 전체를 덮지 않음" }
    sections.zipWithNext { a, b ->
        require(b.startIndex == a.endIndex + 1) { "구간 사이가 끊어짐: ${a.name}, ${b.name}" }
    }
}
