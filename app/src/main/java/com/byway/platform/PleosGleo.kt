package com.byway.platform

import android.content.Context
import android.content.Intent
import android.util.Log
import com.byway.BuildConfig
import com.byway.core.gleo.GleoReply
import com.byway.core.gleo.toJson

/**
 * Gleo Interface Callback(Gleo 음성 연동 설계 2절). 문서 예시대로 패키지를 지정하지 않은 방송으로 보낸다.
 * 디버그 빌드는 보낸 내용을 한 줄 남긴다(에뮬레이터 실측과 시연용 흉내 앱 확인).
 */
object PleosGleo {
    private const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    private const val TAG = "BywayGleo"

    fun respond(context: Context, reply: GleoReply) {
        val json = reply.toJson()
        if (BuildConfig.DEBUG) Log.i(TAG, json)
        runCatching {
            context.sendBroadcast(Intent(RESPONSE_ACTION).putExtra("packageName", context.packageName).putExtra("response", json))
        }.onFailure { Log.e(TAG, "respond failed", it) }
    }
}
