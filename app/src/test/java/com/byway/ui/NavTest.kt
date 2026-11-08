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
        )
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(nav) } }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        assertEquals(nav, restored)
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
