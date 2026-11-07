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
    fun picksNameTheSeasonTheAgeAndTheMood() {
        val tags = CourseTags(moods = listOf("date"), seasons = listOf("spring"), ages = listOf("20s"))
        assertEquals(listOf("봄에 추천", "20대 추천", "데이트"), pickLabels(tags))
    }

    @Test
    fun onlyTheFirstPickOfEachKindIsShown() {
        val tags = CourseTags(moods = listOf("alone", "date"), seasons = listOf("autumn", "spring"), ages = listOf("40s", "50s"))
        assertEquals(listOf("가을에 추천", "40대 추천", "혼자"), pickLabels(tags))
    }

    @Test
    fun aCourseWithoutPicksShowsNone() {
        assertEquals(emptyList<String>(), pickLabels(CourseTags()))
    }

    @Test
    fun themeHasAWord() {
        assertEquals("호수", themeLabel("lake"))
    }

    @Test
    fun unknownThemeHasNoWord() {
        assertNull(themeLabel("desert"))
    }

    /** 테마 타일은 구간과 같은 색을 쓴다. 코스 파일의 구간 분위기는 forest, city, river 뿐이다. */
    @Test
    fun waterThemesTakeTheRiverColor() {
        assertEquals(listOf("river", "river", "river"), listOf("river", "lake", "coast").map(::themeMood))
    }

    @Test
    fun greenThemesTakeTheForestColor() {
        assertEquals(listOf("forest", "forest"), listOf("forest", "mountain").map(::themeMood))
    }

    @Test
    fun cityThemeTakesTheCityColor() {
        assertEquals("city", themeMood("city"))
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
