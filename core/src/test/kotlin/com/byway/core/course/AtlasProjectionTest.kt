package com.byway.core.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 직접 만든 코스와 공유 코스도 내장 코스와 같은 자리 계산으로 한국 지도에 올린다. */
class AtlasProjectionTest {
    private val index = parseSceneIndex(File("../app/src/main/assets/scene/index.json").readText())
    private val courses = File("../app/src/main/assets/courses").listFiles().orEmpty().map { parseCourse(it.readText()) }

    @Test
    fun matchesTheDrawnAtlasForEveryBuiltInCourse() {
        courses.forEach { course ->
            val drawn = requireNotNull(index.courses[course.id]).atlas
            val made = atlasSpotOf(course)
            assertEquals(course.id, drawn.x, made.x, 1f)
            assertEquals(course.id, drawn.y, made.y, 1f)
            assertEquals(course.id, drawn.box.x, made.box.x, 1f)
            assertEquals(course.id, drawn.box.w, made.box.w, 1f)
            assertEquals(course.id, drawn.box.h, made.box.h, 1f)
        }
    }

    /** 파티 핀을 장면 그림 위에 찍을 때 쓴다. 장면 그림의 코스 선 양 끝과 맞아야 한다. */
    @Test
    fun scenePointsMatchTheDrawnRouteEnds() {
        courses.forEach { course ->
            val scene = requireNotNull(index.courses[course.id])
            val first = scene.lines.first().points.first()
            val last = scene.lines.last().points.last()
            val start = scenePointOf(course.path.first(), scene.atlas.frame, index.sceneSize)
            val end = scenePointOf(course.path.last(), scene.atlas.frame, index.sceneSize)
            assertEquals(course.id, first.x, start.x, 3f)
            assertEquals(course.id, first.y, start.y, 3f)
            assertEquals(course.id, last.x, end.x, 3f)
            assertEquals(course.id, last.y, end.y, 3f)
        }
    }

    @Test
    fun linesFollowSectionsAndStayInTheBox() {
        val course = courses.first()
        val spot = atlasSpotOf(course)
        assertEquals(course.sections.size, spot.lines.size)
        assertEquals(course.sections.map { it.mood ?: "" }, spot.lines.map { it.mood })
        val box = spot.box
        assertTrue(spot.lines.flatMap { it.points }.all { it.x in box.x - 1..box.x + box.w + 1 && it.y in box.y - 1..box.y + box.h + 1 })
    }
}
