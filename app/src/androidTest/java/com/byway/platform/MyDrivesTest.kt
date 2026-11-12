package com.byway.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byway.service.DriveTotals
import com.byway.service.MyDrives
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 내 기록과 설정이 파일에 남아 앱을 다시 띄워도 그대로인지 본다. */
@RunWith(AndroidJUnit4::class)
class MyDrivesTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "my_drives_test_${System.nanoTime()}"
    private val copy = "${name}_reopened"
    private val stored = StoredIn(app, name)

    @After
    fun tearDown() {
        app.deleteSharedPreferences(name)
        app.deleteSharedPreferences(copy)
        MyDrives.restore(app)
    }

    /** 프로세스 안에서는 SharedPreferences 가 메모리에 남는다. 파일을 새 이름으로 복사해 열어야 디스크에서 다시 읽는다. */
    private fun reopened(): Context {
        assertTrue(stored.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit())
        val folder = File(app.dataDir, "shared_prefs")
        File(folder, "$name.xml").copyTo(File(folder, "$copy.xml"))
        return StoredIn(app, copy)
    }

    @Test
    fun totalsAddUpAcrossRestart() {
        MyDrives.recordFinished(stored, "a", 1200.5, 3)
        MyDrives.recordFinished(stored, "b", 800.25, 4)

        assertEquals(DriveTotals(2, 2000.75, 7), MyDrives.totals(reopened()))
    }

    @Test
    fun totalsStartEmpty() {
        assertEquals(DriveTotals(), MyDrives.totals(stored))
        assertEquals(emptyList<String>(), MyDrives.recent(stored))
        assertEquals(emptySet<String>(), MyDrives.saved(stored))
    }

    @Test
    fun recentKeepsNewestFirstAcrossRestart() {
        MyDrives.recordFinished(stored, "a", 1.0, 0)
        MyDrives.recordFinished(stored, "b", 1.0, 0)

        assertEquals(listOf("b", "a"), MyDrives.recent(reopened()))
    }

    @Test
    fun drivingACourseAgainMovesItToTheFrontAndStillCounts() {
        MyDrives.recordFinished(stored, "a", 1.0, 0)
        MyDrives.recordFinished(stored, "b", 1.0, 0)
        MyDrives.recordFinished(stored, "a", 1.0, 0)

        assertEquals(listOf("a", "b"), MyDrives.recent(stored))
        assertEquals(3, MyDrives.totals(stored).finishedCourses)
    }

    @Test
    fun recentKeepsTenCourses() {
        (1..12).forEach { MyDrives.recordFinished(stored, "c$it", 1.0, 0) }

        assertEquals((12 downTo 3).map { "c$it" }, MyDrives.recent(reopened()))
    }

    @Test
    fun savedCoursesSurviveRestart() {
        MyDrives.toggleSaved(stored, "a")
        MyDrives.toggleSaved(stored, "b")
        MyDrives.toggleSaved(stored, "a")

        assertEquals(setOf("b"), MyDrives.saved(reopened()))
    }

    @Test
    fun settingsReturnAfterRestart() {
        MyDrives.restore(stored)
        MyDrives.ambienceOn = false
        MyDrives.voiceOn = false
        val file = reopened()

        MyDrives.ambienceOn = true
        MyDrives.voiceOn = true
        MyDrives.restore(file)

        assertFalse(MyDrives.ambienceOn)
        assertFalse(MyDrives.voiceOn)
    }

    @Test
    fun settingsAreOnWhenNothingIsStored() {
        MyDrives.restore(stored)

        assertTrue(MyDrives.ambienceOn)
        assertTrue(MyDrives.voiceOn)
    }

    private class StoredIn(base: Context, private val file: String) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(file, mode)
    }
}
