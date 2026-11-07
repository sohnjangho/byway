package com.byway.platform

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/** 음성 안내. 초기화 전 문구는 모아 뒀다가 읽는다. */
class PleosVoice(context: Context) {
    private val tts = TextToSpeech(context)
    private var ready = false
    private val waiting = mutableListOf<String>()

    fun start() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                ready = true
                waiting.forEach(tts::speak)
                waiting.clear()
            }
            override fun onFailure(errorType: ErrorType) { Log.w("DRIVE", "tts init fail $errorType") }
        })
    }

    fun say(text: String) {
        Log.i("DRIVE", "say $text")
        if (ready) tts.speak(text) else waiting += text
    }

    fun release() = tts.release()
}
