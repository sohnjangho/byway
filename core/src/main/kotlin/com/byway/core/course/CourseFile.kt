package com.byway.core.course

import com.byway.core.geo.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SCHEMA_VERSION = 1

@Serializable
private data class CourseFile(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val attribution: String,
    val path: List<List<Double>>,
    val sections: List<SectionFile>,
)

@Serializable
private data class SectionFile(
    val name: String,
    val startIndex: Int,
    val endIndex: Int,
    val mood: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

fun parseCourse(text: String): Course {
    val file = json.decodeFromString<CourseFile>(text)
    require(file.schemaVersion == SCHEMA_VERSION) { "지원하지 않는 코스 형식: ${file.schemaVersion}" }
    val sections = file.sections.map { Section(it.name, it.startIndex, it.endIndex, it.mood) }
    requireContinuous(sections, file.path.lastIndex)
    return Course(file.id, file.name, file.path.map { (lat, lon) -> GeoPoint(lat, lon) }, sections)
}

private fun requireContinuous(sections: List<Section>, lastIndex: Int) {
    require(sections.first().startIndex == 0 && sections.last().endIndex == lastIndex) { "구간이 코스 전체를 덮지 않음" }
    sections.zipWithNext { a, b ->
        require(b.startIndex == a.endIndex + 1) { "구간 사이가 끊어짐: ${a.name}, ${b.name}" }
    }
}
