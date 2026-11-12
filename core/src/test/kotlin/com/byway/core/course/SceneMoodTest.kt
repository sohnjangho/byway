package com.byway.core.course

import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneMoodTest {
    @Test
    fun rainComesBeforeTimeAndSeason() {
        assertEquals(SceneMood.RAIN, moodFor(Weather.RAIN, hour = 18, month = 10))
    }

    @Test
    fun snowComesBeforeTimeAndSeason() {
        assertEquals(SceneMood.SNOW, moodFor(Weather.SNOW, hour = 17, month = 1))
    }

    @Test
    fun theHourBeforeSunsetIsSunset() {
        assertEquals(SceneMood.SUNSET, moodFor(Weather.CLEAR, hour = 18, month = 6))
    }

    @Test
    fun sunsetComesEarlierInWinter() {
        assertEquals(SceneMood.SUNSET, moodFor(Weather.CLEAR, hour = 17, month = 12))
        assertEquals(SceneMood.NIGHT, moodFor(Weather.CLEAR, hour = 19, month = 12))
    }

    @Test
    fun autumnLeavesFillTheRestOfTheDayInOctober() {
        assertEquals(SceneMood.AUTUMN, moodFor(Weather.CLEAR, hour = 11, month = 10))
    }

    @Test
    fun springBlossomsFillTheRestOfTheDayInApril() {
        assertEquals(SceneMood.SPRING, moodFor(Weather.CLEAR, hour = 11, month = 4))
    }

    @Test
    fun unknownWeatherFallsBackToTimeAndSeason() {
        assertEquals(SceneMood.AUTUMN, moodFor(Weather.UNKNOWN, hour = 11, month = 11))
    }

    @Test
    fun fogAndCloudsComeBeforeTimeAndSeason() {
        assertEquals(SceneMood.FOG, moodFor(Weather.FOG, hour = 18, month = 10))
        assertEquals(SceneMood.CLOUDY, moodFor(Weather.CLOUDY, hour = 18, month = 10))
    }

    @Test
    fun earlyMorningIsSunrise() {
        assertEquals(SceneMood.SUNRISE, moodFor(Weather.CLEAR, hour = 6, month = 4))
    }

    @Test
    fun lateEveningIsNight() {
        assertEquals(SceneMood.NIGHT, moodFor(Weather.CLEAR, hour = 22, month = 7))
    }

    @Test
    fun beforeSunriseIsNight() {
        assertEquals(SceneMood.NIGHT, moodFor(Weather.CLEAR, hour = 3, month = 7))
    }

    @Test
    fun theDaytimeFollowsTheSeason() {
        assertEquals(SceneMood.SUMMER, moodFor(Weather.CLEAR, hour = 11, month = 7))
        assertEquals(SceneMood.WINTER, moodFor(Weather.CLEAR, hour = 11, month = 1))
    }

    @Test
    fun anOrdinaryMayDayIsPlain() {
        assertEquals(SceneMood.PLAIN, moodFor(Weather.CLEAR, hour = 11, month = 5))
    }

    private fun course(id: String, tags: CourseTags) =
        Course(id, id, listOf(GeoPoint(37.0, 127.0), GeoPoint(37.1, 127.0)), listOf(Section("앞", 0, 1)), tags = tags)

    private val lake = course("lake", CourseTags(themes = listOf("lake")))
    private val sunset = course("sunset", CourseTags(times = listOf("sunset")))
    private val autumn = course("autumn", CourseTags(seasons = listOf("autumn")))

    @Test
    fun aRainyDaySuggestsALake() {
        assertEquals("lake", suggestFor(listOf(sunset, lake), SceneMood.RAIN, 11).id)
    }

    @Test
    fun aSunsetSuggestsASunsetCourse() {
        assertEquals("sunset", suggestFor(listOf(lake, sunset), SceneMood.SUNSET, 18).id)
    }

    @Test
    fun autumnSuggestsAnAutumnCourse() {
        assertEquals("autumn", suggestFor(listOf(lake, autumn), SceneMood.AUTUMN, 11).id)
    }

    private fun hill(id: String, top: Double) = Course(
        id, id, listOf(GeoPoint(37.0, 127.0), GeoPoint(37.1, 127.0)), listOf(Section("앞", 0, 1)),
        tags = CourseTags(themes = listOf("forest")), elevation = listOf(10.0, top, 10.0),
    )

    @Test
    fun snowAvoidsMountainCourses() {
        val pass = course("pass", CourseTags(themes = listOf("mountain"), seasons = listOf("winter")))
        assertEquals("lake", suggestFor(listOf(pass, lake), SceneMood.SNOW, 11).id)
    }

    @Test
    fun fogTakesTheLeastClimb() {
        assertEquals("low", suggestFor(listOf(hill("high", 500.0), hill("low", 50.0)), SceneMood.FOG, 11).id)
    }

    @Test
    fun withoutAFittingCourseTheHourDecides() {
        assertEquals("lake", suggestFor(listOf(lake), SceneMood.AUTUMN, 11).id)
    }
}
