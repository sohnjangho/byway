package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {
    private val home = Screen.Browse(Tab.HOME)
    private val ranking = Screen.Browse(Tab.RANKING)

    @Test
    fun timesAndPressScaleAreThePleosValues() {
        assertEquals(listOf(100L, 150L, 250L, 300L, 400L, 450L), MotionSpec.DURATIONS)
        assertEquals(0.95f, MotionSpec.PRESS_SCALE)
    }

    @Test
    fun theFirstScreenAppearsWithoutMotion() {
        assertEquals(ScreenMotion.NONE, screenMotion(null, home, null, null, sameFrame = true))
    }

    @Test
    fun aResizedWindowOrChangedThemeRedrawsWithoutMotion() {
        assertEquals(ScreenMotion.NONE, screenMotion(home, ranking, null, null, sameFrame = false))
    }

    @Test
    fun nothingMovesGoingIntoOrOutOfDriving() {
        listOf(Screen.Drive, Screen.Waiting).forEach { driving ->
            assertEquals(ScreenMotion.NONE, screenMotion(home, driving, null, null, sameFrame = true))
            assertEquals(ScreenMotion.NONE, screenMotion(driving, home, null, null, sameFrame = true))
        }
    }

    @Test
    fun changingMenuSlides() {
        assertEquals(ScreenMotion.SLIDE, screenMotion(home, ranking, null, null, sameFrame = true))
    }

    @Test
    fun openingAppInfoSlides() {
        assertEquals(ScreenMotion.SLIDE, screenMotion(Screen.Browse(Tab.MINE), Screen.About, null, null, sameFrame = true))
    }

    @Test
    fun pickingAnotherCourseOnHomeIsACourseChange() {
        assertEquals(ScreenMotion.COURSE, screenMotion(home, home, "a", "b", sameFrame = true))
    }

    @Test
    fun redrawingTheSameMenuOnlyFades() {
        assertEquals(ScreenMotion.FADE, screenMotion(ranking, ranking, "a", "b", sameFrame = true))
        assertEquals(ScreenMotion.FADE, screenMotion(home, home, "a", "a", sameFrame = true))
    }

    @Test
    fun contentEntersFromTheSideTheMenuMovedToward() {
        assertEquals(1, enterSign(Tab.HOME, Tab.RANKING))
        assertEquals(-1, enterSign(Tab.MINE, Tab.BROWSE))
        assertEquals(0, enterSign(Tab.HOME, Tab.HOME))
    }

    @Test
    fun linesAppearInOrderAndFinishWithinTheLongestTime() {
        assertEquals(0L, staggerDelay(0))
        assertTrue(staggerDelay(1) > staggerDelay(0))
        assertTrue(staggerDelay(50) + MotionSpec.MEDIUM1 <= MotionSpec.LONG2)
    }

    @Test
    fun routeLinesRevealOneAfterAnother() {
        val lengths = listOf(100f, 300f)
        assertEquals(listOf(0f, 0f), revealedLengths(lengths, 0f))
        assertEquals(listOf(100f, 100f), revealedLengths(lengths, 0.5f))
        assertEquals(listOf(100f, 300f), revealedLengths(lengths, 1f))
    }

    @Test
    fun pinsAppearOnlyAfterMostOfTheRouteIsDrawn() {
        assertEquals(0f, pinAlpha(0.5f))
        assertEquals(1f, pinAlpha(1f))
        assertTrue(pinAlpha(0.8f) in 0.01f..0.99f)
    }

    @Test
    fun rubberBandFollowsAtFirstAndNeverPassesTheRange() {
        assertEquals(0f, rubberBand(0f, 400f))
        assertTrue(rubberBand(10f, 400f) in 4f..6f)
        assertTrue(rubberBand(200f, 400f) > rubberBand(100f, 400f))
        assertTrue(rubberBand(1_000_000f, 400f) < 400f)
        assertEquals(-rubberBand(80f, 400f), rubberBand(-80f, 400f))
    }

    @Test
    fun pullStartsOnlyAtAnEdge() {
        assertEquals(0f, pullAfter(0f, 10f, canScrollBack = true, canScrollForward = true))
        assertEquals(10f, pullAfter(0f, 10f, canScrollBack = false, canScrollForward = true))
        assertEquals(-10f, pullAfter(0f, -10f, canScrollBack = true, canScrollForward = false))
    }

    @Test
    fun aListThatFitsDoesNotBounce() {
        assertEquals(0f, pullAfter(0f, 10f, canScrollBack = false, canScrollForward = false))
    }

    @Test
    fun aPullShrinksAndEndsWithoutCrossingTheEdge() {
        assertEquals(15f, pullAfter(20f, -5f, canScrollBack = false, canScrollForward = true))
        assertEquals(0f, pullAfter(20f, -30f, canScrollBack = false, canScrollForward = true))
    }

    @Test
    fun thePhotoFollowsTheFingerBothWays() {
        assertEquals(0.5f, swipeProgress(showingPhoto = false, drag = -200f, distance = 400f))
        assertEquals(0.75f, swipeProgress(showingPhoto = true, drag = 100f, distance = 400f))
        assertEquals(1f, swipeProgress(showingPhoto = false, drag = 900f, distance = 400f))
        assertEquals(0f, swipeProgress(showingPhoto = true, drag = 900f, distance = 400f))
    }

    @Test
    fun aReleasedPhotoSettlesOnTheNearerSideUnlessFlung() {
        assertTrue(settlesOnPhoto(0.6f, 0f, 1000f))
        assertFalse(settlesOnPhoto(0.4f, 0f, 1000f))
        assertTrue(settlesOnPhoto(0.1f, 2000f, 1000f))
        assertFalse(settlesOnPhoto(0.9f, -2000f, 1000f))
    }

    @Test
    fun theMenuMarkMovesEveryEdge() {
        val from = MarkBox(0f, 0f, 100f, 40f)
        val to = MarkBox(200f, 80f, 260f, 120f)
        assertEquals(from, from.toward(to, 0f))
        assertEquals(to, from.toward(to, 1f))
        assertEquals(MarkBox(100f, 40f, 180f, 80f), from.toward(to, 0.5f))
    }
}
