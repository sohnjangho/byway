package com.roadcraft

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** 음성 안내와 위치 SDK가 앱 등록 없이 에뮬레이터에서 동작하는지 확인한다. */
@RunWith(AndroidJUnit4::class)
class VoiceLocationSpikeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val events = CopyOnWriteArrayList<String>()

    private fun record(event: String) {
        Log.i("SPIKE", event)
        events += event
    }

    private fun waitFor(seconds: Int, done: () -> Boolean): Boolean {
        repeat(seconds * 2) {
            if (done()) return true
            Thread.sleep(500)
        }
        return done()
    }

    @Test
    fun ttsSpeaksKoreanGuidance() {
        val tts = TextToSpeech(context)
        val ready = CountDownLatch(1)
        tts.initialize(object : TtsInit {
            override fun onSuccess() { record("tts init ok"); ready.countDown() }
            override fun onFailure(errorType: TtsError) { record("tts init fail $errorType"); ready.countDown() }
        })
        assertTrue("초기화 응답 없음", ready.await(15, TimeUnit.SECONDS))
        tts.addEventListener(object : EventListener {
            override fun onReady() = record("tts ready")
            override fun onStart() = record("tts start")
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onDone() = record("tts done")
            override fun onStop() = record("tts stop")
            override fun onError(errorType: TtsError) = record("tts error $errorType")
        })
        tts.setLocale(Locale.KOREA)
        tts.speak("지금 오른쪽에 노을이 보입니다. 삼 분만 멈추시겠어요?")
        waitFor(25) { events.any { it == "tts done" || it.startsWith("tts error") } }
        tts.release()
        assertTrue("재생 완료 없음: $events", events.contains("tts done"))
    }

    @Test
    fun sttBecomesReady() {
        val stt = SpeechToText(context)
        val ready = CountDownLatch(1)
        stt.initialize(object : SttInit {
            override fun onSuccess() { record("stt init ok"); ready.countDown() }
            override fun onFailure(errorType: SttError) { record("stt init fail $errorType"); ready.countDown() }
        })
        assertTrue("초기화 응답 없음", ready.await(15, TimeUnit.SECONDS))
        stt.addListener(object : ResultListener {
            override fun onReady() = record("stt ready")
            override fun onStartedRecognition() = record("stt started")
            override fun onUpdated(stt: String, completed: Boolean) = record("stt text $stt")
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onEndedRecognition() = record("stt ended")
            override fun onError(errorType: SttError) = record("stt error $errorType")
        })
        stt.request()
        waitFor(10) { events.any { it == "stt ready" || it.startsWith("stt error") } }
        stt.stop()
        Thread.sleep(1_000)
        stt.release()
        assertTrue("인식 준비 없음: $events", events.contains("stt ready"))
    }

    @Test
    fun fusedLocationReturnsPosition() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "pleos.car.permission.FUSED_LOCATION")
        val fused = FusedLocation(context)
        assertTrue("지원 환경 아님", fused.isSupported())
        fused.initialize()
        Thread.sleep(2_000)
        val got = CountDownLatch(1)
        var location: Any? = null
        fused.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location_: LocationData?) { location = location_; record("fused last $location_"); got.countDown() }
            override fun onError(exception: Exception) { record("fused error $exception"); got.countDown() }
        })
        assertTrue("위치 응답 없음", got.await(10, TimeUnit.SECONDS))
        fused.release()
        assertNotNull("위치 없음", location)
    }

    /** 호스트가 geo fix로 반경 안팎을 오가는 동안 구간 진입 알림이 오는지 확인한다. */
    @Test
    fun proximityAlertFiresOnEntry() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "pleos.car.permission.FUSED_LOCATION")
        val fused = FusedLocation(context)
        fused.initialize()
        Thread.sleep(2_000)
        val request = ProximityAlertRequest(latitude = 37.5000, longitude = 127.0000, radiusMeters = 200f, transitionTypes = setOf(ProximityTransition.ENTER))
        fused.addProximityAlert(request, object : ProximityAlertListener {
            override fun onRegistered(alertId: String) = record("proximity registered $alertId")
            override fun onTransition(event: ProximityAlertEvent) = record("proximity transition $event")
            override fun onError(exception: Exception) = record("proximity error $exception")
        })
        waitFor(30) { events.any { it.startsWith("proximity transition") || it.startsWith("proximity error") } }
        fused.release()
        assertTrue("구간 알림 없음: $events", events.any { it.startsWith("proximity transition") })
    }
}
