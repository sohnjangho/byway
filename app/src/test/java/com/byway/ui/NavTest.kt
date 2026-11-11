package com.byway.ui

import com.byway.core.course.CourseFilter
import com.byway.core.course.LengthBand
import com.byway.core.course.NearRange

import com.byway.core.course.RankBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class NavTest {
    /** 걸러 보기는 메뉴를 옮겼다 돌아와도 남는다. 펼친 칩 줄은 닫는다(더 많은 코스 설계 5절). */
    @Test
    fun toTabKeepsFilterButClosesOpenRow() {
        val filter = CourseFilter(query = "노을", mood = "times:sunset")
        val nav = Nav(tab = Tab.BROWSE, filter = filter, openFilter = "mood").toTab(Tab.HOME)
        assertEquals(filter, nav.filter)
        assertEquals(null, nav.openFilter)
    }

    /** 창 막대를 끌면 Activity 가 다시 만들어진다. Bundle 에 담기는 값이어야 고른 화면이 남는다. */
    @Test
    fun everyChoiceSurvivesBeingSaved() {
        val nav = Nav(
            tab = Tab.RANKING,
            courseId = "c1",
            about = true,
            filter = CourseFilter(query = "노을", near = NearRange.KM50, mood = "weathers:rain", length = LengthBand.UP_TO_60, region = "강원", theme = "river"),
            openFilter = "mood",
            rankBy = RankBy.SCENERY,
            atlas = AtlasCamera(1f, 2f, 3f),
            picked = "c2",
            stripX = 7,
            settings = true,
        )
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(nav) } }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        assertEquals(nav, restored)
    }

    /** 함께달리기의 코스 고르기는 메뉴를 바꾸거나 주행하면 닫힌다. */
    @Test
    fun pickingClosesWhenTheMenuChanges() {
        assertEquals(Nav(tab = Tab.HOME), Nav(tab = Tab.TOGETHER, picking = true).toTab(Tab.HOME))
    }

    @Test
    fun pickingClosesWhenDriving() {
        assertEquals(Nav(tab = Tab.TOGETHER), Nav(tab = Tab.TOGETHER, picking = true).whileDriving())
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

    /** 출발하면 나누기 안내는 닫힌다. */
    @Test
    fun drivingClosesSplitGuide() {
        assertNull(Nav(splitGuide = StartRequest("c1", 0)).whileDriving().splitGuide)
        assertNull(Nav(splitGuide = StartRequest("c1", 0), startLayout = DriveLayout.SPLIT).whileDriving().startLayout)
    }

    @Test
    fun tabClosesSplitGuide() {
        assertNull(Nav(splitGuide = StartRequest("c1", 0)).toTab(Tab.HOME).splitGuide)
        assertNull(Nav(splitGuide = StartRequest("c1", 0), startLayout = DriveLayout.NAVI).toTab(Tab.HOME).startLayout)
    }

    /** 메뉴는 탭 내용 위에 떠 있다. 함께달리기처럼 글이 칸 끝까지 가는 탭은 메뉴 자리를 비운다(넓은 창은 왼쪽, 1/3 칸은 위). */
    @Test
    fun railInsetLeavesRoomForTheMenu() {
        assertEquals(0 to Dimens.RAIL_TOP_HEIGHT, railInset(WindowClass.THIRD))
        assertEquals(Dimens.RAIL_WIDTH to 0, railInset(WindowClass.TWO))
        assertEquals(Dimens.RAIL_WIDTH to 0, railInset(WindowClass.FULL))
    }
}
