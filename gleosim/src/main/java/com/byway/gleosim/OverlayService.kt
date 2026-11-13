package com.byway.gleosim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 다른 앱 위에 떠 있는 시연용 대화창(설계 9절). 말 버튼을 누르면 Gleo 와 같은 규격의 방송을 에움길에 보내고,
 * 에움길의 Callback 을 받아 한국어 답 말풍선을 보인다. 실제 Gleo 가 아님을 늘 보이고 Gleo 로고·색은 쓰지 않는다.
 */
class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private lateinit var panel: View
    private lateinit var userBubble: TextView
    private lateinit var replyBubble: TextView
    private lateinit var voice: SimVoice
    private val noAnswer = Runnable { replyBubble.text = "응답이 없어요" }

    private val responses = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("packageName") != BYWAY) return
            val response = intent.getStringExtra("response") ?: return
            Log.i(TAG, "response $response")
            main.removeCallbacks(noAnswer)
            val text = koreanReply(response)
            replyBubble.text = text
            val mode = runCatching { (Json.parseToJsonElement(response).jsonObject["mode"] as? JsonPrimitive)?.content }.getOrNull()
            if (mode == null || mode == "voice") voice.say(text)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        voice = SimVoice(this).also { it.start() }
        windows = getSystemService(WindowManager::class.java)
        panel = buildPanel()
        windows.addView(panel, WindowManager.LayoutParams(
            dp(1000), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(96)
        })
        val filter = IntentFilter(RESPONSE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(responses, filter, RECEIVER_EXPORTED) else registerReceiver(responses, filter)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        unregisterReceiver(responses)
        windows.removeView(panel)
        voice.release()
        super.onDestroy()
    }

    private fun send(scenario: Scenario) {
        userBubble.text = scenario.utterance
        replyBubble.text = "…"
        Log.i(TAG, "send ${scenario.function} ${scenario.paramJson}")
        sendBroadcast(Intent(scenario.function).setPackage(BYWAY).putExtra("ACTION_PARAM", scenario.paramJson))
        main.removeCallbacks(noAnswer)
        main.postDelayed(noAnswer, NO_ANSWER_MS)
    }

    private fun buildPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = box(Color.parseColor("#F2F3F5"), Color.parseColor("#9AA0A6"))
        val pad = dp(24)
        setPadding(pad, pad, pad, pad)
        addView(text("시연용 Gleo 흉내 · 실제 Gleo 아님", 26f, Color.parseColor("#5F6368")))
        userBubble = text("", 38f, Color.BLACK).apply { gravity = Gravity.END }
        replyBubble = text("말 버튼을 눌러 보세요", 38f, Color.BLACK).apply { background = box(Color.WHITE, Color.parseColor("#DADCE0")) }
        addView(userBubble, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        addView(replyBubble, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        SCENARIOS.chunked(2).forEach { pair ->
            addView(LinearLayout(context).apply {
                pair.forEach { scenario ->
                    addView(Button(context).apply {
                        text = scenario.utterance
                        isAllCaps = false
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                        setOnClickListener { send(scenario) }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        addView(Button(context).apply {
            text = "닫기"
            setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
    }

    private fun text(value: String, sp: Float, color: Int) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        val pad = dp(6)
        setPadding(pad, pad, pad, pad)
    }

    private fun box(fill: Int, line: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), line)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 화면 뒤로 가도 대화창과 Callback 수신이 살아 있게 포그라운드 서비스로 둔다. */
    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Gleo 흉내", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("시연용 Gleo 흉내 실행 중")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, notification)
    }

    private companion object {
        const val TAG = "GleoSim"
        const val BYWAY = "com.byway"
        const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        const val CHANNEL = "gleosim"
        const val NO_ANSWER_MS = 7_000L
    }
}
