package com.byway.service

import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 위치는 1초마다 오지만 화면은 처음 받았을 때와 1km 넘게 움직였을 때만 다시 그린다. */
class BrowseLocationTest {
    private val p = GeoPoint(37.5663, 126.9779)

    @Test
    fun tellsFirstFixAndBigMovesOnly() {
        assertTrue(BrowseLocation.shouldTell(null, p))
        assertFalse(BrowseLocation.shouldTell(p, GeoPoint(p.lat + 0.0045, p.lon)))
        assertTrue(BrowseLocation.shouldTell(p, GeoPoint(p.lat + 0.011, p.lon)))
    }

    /** 위치 권한이 없으면 위치를 묻지 않는다. 앱은 출발·기록 때만 권한을 묻는다(검토 I-3). */
    @Test
    fun startsOnlyWithPermission() {
        assertTrue(BrowseLocation.shouldStart(on = true, granted = true, running = false))
        assertFalse(BrowseLocation.shouldStart(on = true, granted = false, running = false))
        assertFalse(BrowseLocation.shouldStart(on = false, granted = true, running = false))
        assertFalse(BrowseLocation.shouldStart(on = true, granted = true, running = true))
    }
}
