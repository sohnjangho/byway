package com.byway.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedMusicTest {
    @Test
    fun eachMoodAsksForDrivingMusic() {
        assertEquals("숲길 드라이브 음악", musicQuery("forest"))
        assertEquals("바닷가 드라이브 음악", musicQuery("river"))
        assertEquals("도심 드라이브 음악", musicQuery("city"))
    }

    @Test
    fun anUnknownMoodStillAsksForDrivingMusic() {
        assertEquals("드라이브 음악", musicQuery("desert"))
    }

    @Test
    fun theChosenAppComesFirst() {
        assertEquals("b.app", pickMusicApp(listOf("a.app", "b.app"), "b.app"))
    }

    @Test
    fun aMusicAppComesBeforeThePhoneLink() {
        assertEquals("a.app", pickMusicApp(listOf("com.android.bluetooth", "a.app"), null))
    }

    @Test
    fun thePhoneLinkIsUsedWhenItIsTheOnlyOne() {
        assertEquals("com.android.bluetooth", pickMusicApp(listOf("com.android.bluetooth"), null))
    }

    @Test
    fun aChosenAppThatWasRemovedIsIgnored() {
        assertEquals("a.app", pickMusicApp(listOf("a.app"), "gone.app"))
    }

    @Test
    fun withoutAnyMusicAppNothingIsPicked() {
        assertNull(pickMusicApp(emptyList(), null))
    }
}
