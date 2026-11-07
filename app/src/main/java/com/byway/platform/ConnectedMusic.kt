package com.byway.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.util.Log

private const val PHONE_LINK = "com.android.bluetooth"
private val QUERIES = mapOf("forest" to "숲길 드라이브 음악", "river" to "바닷가 드라이브 음악", "city" to "도심 드라이브 음악")

/** 음악 앱에 보낼 검색어. 곡을 앱에 넣지 않고 운전자가 쓰는 음악 앱에서 찾아 튼다. */
fun musicQuery(track: String): String = QUERIES[track] ?: "드라이브 음악"

/** 고른 앱이 먼저다. 없으면 검색을 받을 수 있는 음악 앱을 휴대폰 연결보다 앞세운다. */
fun pickMusicApp(packages: List<String>, chosen: String?): String? =
    chosen?.takeIf { it in packages } ?: packages.firstOrNull { it != PHONE_LINK } ?: packages.firstOrNull()

/** 차에 연결된 음악 앱을 안드로이드 표준 미디어 브라우저로 제어한다. 음악 앱이 없으면 아무 일도 하지 않는다. */
class ConnectedMusic(private val context: Context) {
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private var waiting: String? = null
    private var started = false

    fun apps(): List<ComponentName> =
        context.packageManager.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }

    fun play(query: String, chosen: String? = null) {
        val active = controller
        if (active != null) return send(active, query)
        val services = apps()
        val app = pickMusicApp(services.map { it.packageName }, chosen)
        if (app == null) {
            Log.i(TAG, "no music app")
            return
        }
        waiting = query
        browser = MediaBrowser(context, services.first { it.packageName == app }, callback, null).also { it.connect() }
    }

    /** 코스가 튼 음악만 멈춘다. 운전자가 듣던 음악은 건드리지 않는다. */
    fun pause() {
        if (started) controller?.transportControls?.pause()
        started = false
    }

    fun release() {
        browser?.disconnect()
        browser = null
        controller = null
    }

    private val callback = object : MediaBrowser.ConnectionCallback() {
        override fun onConnected() {
            val connected = MediaController(context, requireNotNull(browser).sessionToken)
            controller = connected
            waiting?.let { send(connected, it) }
            waiting = null
        }

        override fun onConnectionFailed() {
            Log.w(TAG, "music app refused")
        }
    }

    private fun send(active: MediaController, query: String) {
        val canSearch = (active.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L
        if (canSearch) active.transportControls.playFromSearch(query, Bundle()) else active.transportControls.play()
        started = true
        Log.i(TAG, "music $query search=$canSearch")
    }

    private companion object {
        const val TAG = "DRIVE"
    }
}
