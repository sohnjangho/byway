package com.byway.ui

import com.byway.core.course.CourseTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagLabelsTest {
    @Test
    fun everyKnownTagHasAWord() {
        val tags = CourseTags(
            themes = listOf("river", "forest", "mountain", "lake", "city", "coast"),
            moods = listOf("alone", "date", "family"),
            times = listOf("sunset", "sunrise", "day", "night"),
        )
        assertEquals(13, tagLabels(tags).toSet().size)
    }

    @Test
    fun themesComeFirstThenTimesThenMoods() {
        val tags = CourseTags(themes = listOf("river"), moods = listOf("date"), times = listOf("sunset"))
        assertEquals(listOf("강변", "노을", "데이트"), tagLabels(tags))
    }

    @Test
    fun unknownTagIsLeftOut() {
        assertEquals(listOf("강변"), tagLabels(CourseTags(themes = listOf("desert", "river"))))
    }

    @Test
    fun noTagsGiveNoLabels() {
        assertEquals(emptyList<String>(), tagLabels(CourseTags()))
    }

    @Test
    fun spotKindHasAWord() {
        assertEquals("전망", spotKindLabel("view"))
    }

    @Test
    fun unknownSpotKindHasNoWord() {
        assertNull(spotKindLabel("toilet"))
    }
}
