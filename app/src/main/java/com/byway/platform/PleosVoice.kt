package com.byway.platform

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/** 음성 안내. 초기화 전 문구는 모아 뒀다가 읽는다. */
class PleosVoice(context: Context) {
    private val tts = TextToSpeech(context)
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private val waiting = mutableListOf<String>()
    private var tone: ToneGenerator? = null

    fun start() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                ready = true
                waiting.forEach(tts::speak)
                waiting.clear()
            }
            override fun onFailure(errorType: ErrorType) { Log.w(TAG, "tts init fail $errorType") }
        })
    }

    /** [delayMs]만큼 뒤에 읽는다. 내비가 새 경로를 말하는 동안 겹치지 않게 미룰 때 쓴다. */
    fun say(text: String, delayMs: Long = 0) {
        Log.i(TAG, "say $text" + if (delayMs > 0) " (+${delayMs}ms)" else "")
        if (delayMs > 0) handler.postDelayed({ speak(text) }, delayMs) else speak(text)
    }

    private fun speak(text: String) {
        if (ready) tts.speak(text) else waiting += text
    }

    /** 음성 안내를 꺼 두었을 때 이탈·복귀·경로 교체를 알리는 짧은 소리. 소리를 낼 수 없는 차에서는 조용히 넘어간다. */
    fun chime() {
        Log.i(TAG, "chime")
        try {
            val player = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, CHIME_VOLUME).also { tone = it }
            player.startTone(ToneGenerator.TONE_PROP_BEEP, CHIME_MS)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "chime skipped: $failure")
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        tts.release()
    }

    private companion object {
        const val TAG = "DRIVE"
        const val CHIME_VOLUME = 60
        const val CHIME_MS = 180
    }
}
