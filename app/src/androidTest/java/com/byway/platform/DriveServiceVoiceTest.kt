package com.byway.platform

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byway.service.DriveBoard
import com.byway.service.DriveService
import com.byway.service.MyDrives
import com.byway.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 실제 서비스로 코스를 시작해 음성 설정이 TTS 앞에서 막히는지 본다. PleosVoice 가 말할 때 남기는 로그로 판단한다. */
@RunWith(AndroidJUnit4::class)
class DriveServiceVoiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val wasOn = MyDrives.voiceOn
    private val activity by lazy {
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Before
    fun setUp() {
        instrumentation.uiAutomation
            .executeShellCommand("pm grant --user 10 com.byway pleos.car.permission.FUSED_LOCATION").close()
        activity
    }

    @After
    fun tearDown() {
        DriveService.stop(app)
        await { !DriveBoard.state.running }
        DriveBoard.update { it.copy(spoken = "") }
        MyDrives.voiceOn = wasOn
        activity.finish()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(condition())
    }

    private fun linesSaidAtStart(): List<String> {
        val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        DriveService.start(activity, COURSE)
        await { DriveBoard.state.spoken.isNotEmpty() }
        val log = ProcessBuilder("logcat", "-d", "-s", "DRIVE:I", "-T", since).start()
        return log.inputStream.bufferedReader().readLines().filter { "say " in it }
    }

    @Test
    fun voiceOnSpeaksTheStartLine() {
        MyDrives.voiceOn = true

        assertEquals(1, linesSaidAtStart().size)
    }

    @Test
    fun voiceOffStaysQuietButKeepsTheCaption() {
        MyDrives.voiceOn = false

        assertEquals(emptyList<String>(), linesSaidAtStart())
    }

    private companion object {
        const val COURSE = "gonjiam-forest"
        const val WAIT_MS = 10_000L
    }
}
