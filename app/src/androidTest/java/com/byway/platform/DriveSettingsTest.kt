package com.byway.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byway.core.course.factsOf
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature
import com.byway.core.drive.DriveHost
import com.byway.core.engine.CourseEngine
import com.byway.service.CourseCatalog
import com.byway.service.DriveTotals
import com.byway.service.FinishRecorder
import com.byway.service.MyDrives
import com.byway.service.courseFeatures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 코스와 엔진으로 달려 기록과 설정이 주행에 닿는지 본다. */
@RunWith(AndroidJUnit4::class)
class DriveSettingsTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "drive_settings_test_${System.nanoTime()}"
    private val stored = object : ContextWrapper(app) {
        override fun getSharedPreferences(file: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(name, mode)
    }
    private val course = CourseCatalog.find(app, "paldang-sunset")

    @Before
    fun setUp() = MyDrives.restore(stored)

    @After
    fun tearDown() {
        app.deleteSharedPreferences(name)
        MyDrives.restore(app)
    }

    private fun hostWith(features: List<DriveFeature>) = DriveHost(CourseEngine(), features).also {
        it.dispatch(DriveEvent.DrivingStateChanged(true))
    }

    private fun drive(host: DriveHost, stops: Int) {
        host.dispatch(DriveEvent.CourseStarted(course))
        course.path.take(stops).forEach { host.dispatch(DriveEvent.LocationUpdated(it)) }
    }

    @Test
    fun finishingACourseRecordsItsDistanceAndCurves() {
        drive(hostWith(listOf(FinishRecorder(stored))), course.path.size)

        val facts = factsOf(course)
        assertEquals(DriveTotals(1, facts.meters, facts.curves), MyDrives.totals(stored))
        assertEquals(listOf(course.id), MyDrives.recent(stored))
    }

    @Test
    fun stoppingMidwayRecordsNothing() {
        drive(hostWith(listOf(FinishRecorder(stored))), course.path.size / 2)

        assertEquals(DriveTotals(), MyDrives.totals(stored))
        assertEquals(emptyList<String>(), MyDrives.recent(stored))
    }

    private fun cabinCommandsAtStart(): List<DriveCommand> =
        hostWith(courseFeatures()).dispatch(DriveEvent.CourseStarted(course)).filterIsInstance<DriveCommand.SetCabin>()

    @Test
    fun ambienceOnSetsTheCabin() {
        MyDrives.ambienceOn = true

        assertTrue(cabinCommandsAtStart().isNotEmpty())
    }

    @Test
    fun ambienceOffLeavesTheCabinAlone() {
        MyDrives.ambienceOn = false

        assertEquals(emptyList<DriveCommand>(), cabinCommandsAtStart())
    }
}
