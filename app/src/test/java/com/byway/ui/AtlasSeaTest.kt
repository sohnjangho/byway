package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AtlasSeaTest {
    /** 지도 종류와 낮·밤마다 한국 지도 그림의 바다색이 다르다. 그 색으로 칠해야 녹인 가장자리가 사각형으로 보이지 않는다. */
    @Test
    fun seaColorFollowsStyleAndNight() {
        assertEquals(0xFF91A3AF.toInt(), seaColor(MapStyle.MAP, night = false))
        assertEquals(0xFF131419.toInt(), seaColor(MapStyle.MAP, night = true))
        assertEquals(0xFF182B4C.toInt(), seaColor(MapStyle.SATELLITE, night = false))
        assertEquals(0xFF0A1930.toInt(), seaColor(MapStyle.SATELLITE, night = true))
    }
}
