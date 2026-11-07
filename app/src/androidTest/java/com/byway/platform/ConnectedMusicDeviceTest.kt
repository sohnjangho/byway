package com.byway.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 이 차량 OS에서 음악 앱을 찾을 수 있는지 본다. 매니페스트 queries 가 빠지면 목록이 빈다. */
@RunWith(AndroidJUnit4::class)
class ConnectedMusicDeviceTest {
    @Test
    fun theCarListsItsMusicApps() {
        val music = ConnectedMusic(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(music.apps().isNotEmpty())
    }
}
