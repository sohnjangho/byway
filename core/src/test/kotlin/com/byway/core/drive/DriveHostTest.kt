package com.byway.core.drive

import com.byway.core.course.Section
import com.byway.core.drive.DriveCommand.Publish
import com.byway.core.drive.DriveCommand.Say
import com.byway.core.drive.DriveCommand.ShowPanel
import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveHostTest {
    private val location = DriveEvent.LocationUpdated(GeoPoint(37.0, 127.0))
    private val entered = DriveEvent.SectionEntered(Section("강변", 0, 1))

    private fun feature(name: String, react: (DriveEvent) -> List<DriveCommand>) = object : DriveFeature {
        override val id = name
        override fun onEvent(event: DriveEvent) = react(event)
    }

    private val silentEngine = feature("course") { emptyList() }

    @Test
    fun engineSpeaksBeforeFeatures() {
        val host = DriveHost(feature("course") { listOf(Say("엔진")) }, listOf(feature("a") { listOf(Say("기능")) }))
        assertEquals(listOf(Say("엔진"), Say("기능")), host.dispatch(location))
    }

    @Test
    fun publishedEventsReachFeatures() {
        val engine = feature("course") { if (it == location) listOf(Publish(entered)) else emptyList() }
        val mood = feature("mood") { if (it == entered) listOf(Say("연출")) else emptyList() }
        assertEquals(listOf(Say("연출")), DriveHost(engine, listOf(mood)).dispatch(location))
    }

    @Test
    fun publishNeverLeavesTheHost() {
        val host = DriveHost(feature("course") { if (it == location) listOf(Publish(entered)) else emptyList() }, emptyList())
        assertTrue(host.dispatch(location).isEmpty())
    }

    @Test
    fun aBrokenFeatureDoesNotStopTheEngine() {
        val broken = feature("broken") { error("고장") }
        val host = DriveHost(feature("course") { listOf(Say("엔진")) }, listOf(broken))
        assertEquals(listOf(Say("엔진")), host.dispatch(location))
    }

    @Test
    fun aBrokenFeatureDoesNotStopOtherFeatures() {
        val host = DriveHost(silentEngine, listOf(feature("broken") { error("고장") }, feature("ok") { listOf(Say("정상")) }))
        assertEquals(listOf(Say("정상")), host.dispatch(location))
    }

    @Test
    fun aBrokenFeatureIsReportedAndSwitchedOff() {
        var calls = 0
        val failed = mutableListOf<String>()
        val broken = feature("broken") { calls++; error("고장") }
        val host = DriveHost(silentEngine, listOf(broken)) { id, _ -> failed += id }
        host.dispatch(location)
        host.dispatch(location)
        assertEquals(1, calls)
        assertEquals(listOf("broken"), failed)
    }

    @Test
    fun panelsAreBlockedWhileDriving() {
        val host = DriveHost(silentEngine, listOf(feature("list") { listOf(ShowPanel("spots")) }))
        assertTrue(host.dispatch(location).isEmpty())
    }

    @Test
    fun panelsOpenOnceParked() {
        val host = DriveHost(silentEngine, listOf(feature("list") { listOf(ShowPanel("spots")) }))
        host.dispatch(DriveEvent.DrivingStateChanged(parked = true))
        assertEquals(listOf(ShowPanel("spots")), host.dispatch(location))
    }

    @Test
    fun panelsCloseAgainWhenDrivingResumes() {
        val host = DriveHost(silentEngine, listOf(feature("list") { listOf(ShowPanel("spots")) }))
        host.dispatch(DriveEvent.DrivingStateChanged(parked = true))
        host.dispatch(DriveEvent.DrivingStateChanged(parked = false))
        assertTrue(host.dispatch(location).isEmpty())
    }

    @Test
    fun handsOffStillCountsAsDriving() {
        val host = panelHost()
        host.dispatch(DriveEvent.DriveModeChanged(DriveMode.HANDS_OFF))
        assertTrue(host.dispatch(location).isEmpty())
    }

    @Test
    fun panelsOpenWhenTheDriverMayLookAway() {
        val host = panelHost()
        host.dispatch(DriveEvent.DriveModeChanged(DriveMode.EYES_OFF))
        assertEquals(listOf(ShowPanel("spots")), host.dispatch(location))
    }

    @Test
    fun panelsCloseAtOnceWhenTheDriverTakesOver() {
        val host = panelHost()
        host.dispatch(DriveEvent.DriveModeChanged(DriveMode.EYES_OFF))
        host.dispatch(DriveEvent.DriveModeChanged(DriveMode.MANUAL))
        assertTrue(host.dispatch(location).isEmpty())
    }

    @Test
    fun parkingAndDrivingModeAreIndependent() {
        val host = panelHost()
        host.dispatch(DriveEvent.DriveModeChanged(DriveMode.EYES_OFF))
        host.dispatch(DriveEvent.DrivingStateChanged(parked = false))
        assertEquals(listOf(ShowPanel("spots")), host.dispatch(location))
    }

    private fun panelHost() = DriveHost(silentEngine, listOf(feature("list") { listOf(ShowPanel("spots")) }))

    @Test
    fun voiceIsAllowedWhileDriving() {
        val host = DriveHost(silentEngine, listOf(feature("voice") { listOf(Say("안내")) }))
        assertEquals(listOf(Say("안내")), host.dispatch(location))
    }
}
