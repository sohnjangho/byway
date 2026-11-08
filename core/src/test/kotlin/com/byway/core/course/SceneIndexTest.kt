package com.byway.core.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneIndexTest {
    private val text = """
        {"sceneSize":[2404,1210],"koreaSize":[2020,2347],
         "labels":[{"name":"수도권","kind":"region","x":1.5,"y":2.5}],
         "moods":{"rain":{"shape":"streak","count":46,"night":"#b9d2ec","day":"#5c7f9f","wash":"rgba(80, 110, 145, 0.26)","dx":-140,"dy":640,"near":0.8,"far":1.3}},
         "courses":{"c1":{
           "lines":[{"mood":"city","name":"구간","points":[[10.0,20.0],[30.5,40.0]]}],
           "places":[{"no":1,"name":"전망대","kind":"view","label":"경치","note":"","km":1.5,"x":10.0,"y":20.0}],
           "atlas":{"x":1.0,"y":2.0,"box":{"x":1,"y":2,"w":3,"h":4},"frame":{"x":5,"y":6,"w":7,"h":8},
                    "lines":[{"mood":"river","points":[[1.0,2.0]]}]}}}}
    """.trimIndent()

    @Test
    fun parsesLinesPlacesAndAtlas() {
        val index = parseSceneIndex(text)
        val scene = index.courses.getValue("c1")
        assertEquals(Px(2404f, 1210f), index.sceneSize)
        assertEquals(Px(2020f, 2347f), index.koreaSize)
        assertEquals(listOf(AtlasLabel("수도권", "region", 1.5f, 2.5f)), index.labels)
        assertEquals(SceneLine("city", "구간", listOf(Px(10f, 20f), Px(30.5f, 40f))), scene.lines.single())
        assertEquals(ScenePlace(1, "전망대", "view", "경치", "", 1.5, 10f, 20f), scene.places.single())
        assertEquals(Box(5f, 6f, 7f, 8f), scene.atlas.frame)
    }

    @Test
    fun missingCourseIsAbsentNotCrash() {
        assertNull(parseSceneIndex(text).courses["없음"])
    }

    @Test
    fun atlasLineWithoutNameParses() {
        val line = parseSceneIndex(text).courses.getValue("c1").atlas.lines.single()
        assertEquals(SceneLine("river", "", listOf(Px(1f, 2f))), line)
    }

    @Test
    fun moodLooksAreRead() {
        val rain = parseSceneIndex(text).moods.getValue("rain")
        assertEquals(MoodLook("streak", 46, "#b9d2ec", "#5c7f9f", "rgba(80, 110, 145, 0.26)", -140f, 640f, 0.8f, 1.3f), rain)
    }

    @Test
    fun emptyIndexHasNoCourses() {
        assertEquals(emptyMap<String, CourseScene>(), SceneIndex.EMPTY.courses)
    }
}
