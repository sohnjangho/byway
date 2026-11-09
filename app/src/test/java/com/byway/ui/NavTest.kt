package com.byway.ui

import com.byway.core.course.RankBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class NavTest {
    /** 창 막대를 끌면 Activity 가 다시 만들어진다. Bundle 에 담기는 값이어야 고른 화면이 남는다. */
    @Test
    fun everyChoiceSurvivesBeingSaved() {
        val nav = Nav(
            tab = Tab.RANKING,
            courseId = "c1",
            about = true,
            theme = "river",
            region = "강원",
            rankBy = RankBy.CLIMB,
            atlas = AtlasCamera(1f, 2f, 3f),
            picked = "c2",
            stripX = 7,
            settings = true,
            scrub = false,
        )
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(nav) } }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        assertEquals(nav, restored)
    }

    @Test
    fun pickingAMenuClosesSettingsAndAbout() {
        val nav = Nav(settings = true, about = true, picked = "c1", atlas = AtlasCamera(1f, 2f, 3f)).toTab(Tab.RANKING)
        assertEquals(Nav(tab = Tab.RANKING), nav)
    }

    @Test
    fun drivingClosesSettingsAndAboutButKeepsTheRest() {
        val nav = Nav(tab = Tab.BROWSE, courseId = "c1", settings = true, about = true).whileDriving()
        assertEquals(Nav(tab = Tab.BROWSE, courseId = "c1"), nav)
    }

    /** 설정은 메뉴 맨 아래에 있다. 내용은 메뉴가 옮겨 간 쪽에서 들어온다. */
    @Test
    fun settingsSitsBelowEveryMenu() {
        Tab.entries.forEach { assertTrue(railPosition(Nav(tab = it)) < railPosition(Nav(tab = it, settings = true))) }
        assertEquals(railPosition(Nav(settings = true)), railPosition(Nav(settings = true, about = true)))
        assertTrue(railPosition(Nav(tab = Tab.HOME)) < railPosition(Nav(tab = Tab.RANKING)))
    }

    @Test
    fun noMenuIsMarkedWhileSettingsIsOpen() {
        assertEquals(Tab.MINE, markedTab(Nav(tab = Tab.MINE)))
        assertEquals(null, markedTab(Nav(tab = Tab.MINE, settings = true)))
        assertEquals(null, markedTab(Nav(tab = Tab.MINE, about = true)))
    }

    @Test
    fun windowClassFollowsWidth() {
        assertEquals(WindowClass.THIRD, windowClassOf(841))
        assertEquals(WindowClass.TWO, windowClassOf(1700))
        assertEquals(WindowClass.FULL, windowClassOf(2560))
    }

    @Test
    fun splitPaneIsNarrow() {
        assertFalse(isWide(841))
    }

    @Test
    fun fullPaneIsWide() {
        assertTrue(isWide(1682))
    }
}
