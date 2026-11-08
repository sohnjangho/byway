package com.byway.core.course

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 장면 그림 또는 한국 지도 위의 픽셀 좌표. */
data class Px(val x: Float, val y: Float)

data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

data class SceneLine(val mood: String, val name: String, val points: List<Px>)

data class ScenePlace(
    val no: Int,
    val name: String,
    val kind: String,
    val label: String,
    val note: String,
    val km: Double,
    val x: Float,
    val y: Float,
)

/** 한국 지도 위 코스 자리. [frame]은 장면 그림이 겹치는 자리다. */
data class AtlasSpot(val x: Float, val y: Float, val box: Box, val frame: Box, val lines: List<SceneLine>)

data class CourseScene(val lines: List<SceneLine>, val places: List<ScenePlace>, val atlas: AtlasSpot)

data class AtlasLabel(val name: String, val kind: String, val x: Float, val y: Float)

/** 분위기 효과 값. 시안의 tools/make-effects.py 와 같은 원본이다. */
data class MoodLook(
    val shape: String,
    val count: Int,
    val night: String,
    val day: String,
    val wash: String,
    val dx: Float,
    val dy: Float,
    val near: Float,
    val far: Float,
)

data class SceneIndex(
    val sceneSize: Px,
    val koreaSize: Px,
    val labels: List<AtlasLabel>,
    val moods: Map<String, MoodLook>,
    val courses: Map<String, CourseScene>,
) {
    companion object {
        /** 자료를 읽지 못했을 때. 화면은 코스 그림으로 대신한다. */
        val EMPTY = SceneIndex(Px(0f, 0f), Px(0f, 0f), emptyList(), emptyMap(), emptyMap())
    }
}

@Serializable
private data class IndexFile(
    val sceneSize: List<Float>,
    val koreaSize: List<Float>,
    val labels: List<LabelFile>,
    val moods: Map<String, LookFile>,
    val courses: Map<String, SceneCourseFile>,
)

@Serializable
private data class LabelFile(val name: String, val kind: String, val x: Float, val y: Float)

@Serializable
private data class LookFile(
    val shape: String,
    val count: Int,
    val night: String,
    val day: String,
    val wash: String,
    val dx: Float,
    val dy: Float,
    val near: Float,
    val far: Float,
)

@Serializable
private data class SceneCourseFile(val lines: List<LineFile>, val places: List<PlaceFile>, val atlas: AtlasFile)

@Serializable
private data class LineFile(val mood: String, val name: String = "", val points: List<List<Float>>)

@Serializable
private data class PlaceFile(
    val no: Int,
    val name: String,
    val kind: String,
    val label: String,
    val note: String,
    val km: Double,
    val x: Float,
    val y: Float,
)

@Serializable
private data class BoxFile(val x: Float, val y: Float, val w: Float, val h: Float)

@Serializable
private data class AtlasFile(val x: Float, val y: Float, val box: BoxFile, val frame: BoxFile, val lines: List<LineFile>)

private val json = Json { ignoreUnknownKeys = true }

fun parseSceneIndex(text: String): SceneIndex {
    val file = json.decodeFromString<IndexFile>(text)
    return SceneIndex(
        sceneSize = file.sceneSize.toPx(),
        koreaSize = file.koreaSize.toPx(),
        labels = file.labels.map { AtlasLabel(it.name, it.kind, it.x, it.y) },
        moods = file.moods.mapValues { (_, l) -> MoodLook(l.shape, l.count, l.night, l.day, l.wash, l.dx, l.dy, l.near, l.far) },
        courses = file.courses.mapValues { (_, c) -> c.toScene() },
    )
}

private fun List<Float>.toPx() = Px(this[0], this[1])

private fun BoxFile.toBox() = Box(x, y, w, h)

private fun LineFile.toLine() = SceneLine(mood, name, points.map { it.toPx() })

private fun SceneCourseFile.toScene() = CourseScene(
    lines = lines.map { it.toLine() },
    places = places.map { ScenePlace(it.no, it.name, it.kind, it.label, it.note, it.km, it.x, it.y) },
    atlas = AtlasSpot(atlas.x, atlas.y, atlas.box.toBox(), atlas.frame.toBox(), atlas.lines.map { it.toLine() }),
)
