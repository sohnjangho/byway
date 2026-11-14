package com.byway.gleosim

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/** 답 말풍선을 읽는 목소리. 에움길의 PleosVoice 와 같은 Pleos TTS 를 쓴다. */
class SimVoice(context: Context) {
    private val tts = TextToSpeech(context)
    private var ready = false

    fun start() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                ready = true
            }
            override fun onFailure(errorType: ErrorType) { Log.w(TAG, "tts init fail $errorType") }
        })
    }

    fun say(text: String) {
        if (ready) tts.speak(text)
    }

    fun release() = tts.release()

    private companion object {
        const val TAG = "GleoSim"
    }
}
