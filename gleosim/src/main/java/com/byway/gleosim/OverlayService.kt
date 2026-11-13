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
import android.graphics.Typeface
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
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 시연용 Gleo 화면(설계 9절, PM 11-13 "Gleo AI 디자인을 보고"). 디자인 가이드의 크롬·말풍선·패널(목록형)을 따른다.
 * 운전석 쪽(왼쪽 차량 정보 칸 위)에 뜨고, 3분할의 내비·에움길 칸은 가리지 않는다.
 * 말은 adb 로 넣는다: `am broadcast -a com.byway.gleosim.SAY -p com.byway.gleosim --ei n <SCENARIOS 번호>`.
 * 그러면 듣는 중 → 사용자 말 → 생각 중(에움길에 Gleo 규격 방송) → 답 말풍선과 패널 → 잠시 뒤 사라짐 순서로 보인다.
 */
class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private lateinit var cover: View
    private lateinit var chrome: ChromeView
    private lateinit var stack: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var panel: LinearLayout
    private lateinit var voice: SimVoice
    private var waiting = false

    private val noAnswer = Runnable {
        waiting = false
        showReply("응답이 없어요", null)
        main.postDelayed(::sleep, STANDBY_MS)
    }

    private val says = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            SCENARIOS.getOrNull(intent.getIntExtra("n", -1))?.let(::converse)
        }
    }

    private val responses = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("packageName") != BYWAY || !waiting) return
            val response = intent.getStringExtra("response") ?: return
            Log.i(TAG, "response $response")
            waiting = false
            main.removeCallbacks(noAnswer)
            val text = koreanReply(response)
            val mode = runCatching { (Json.parseToJsonElement(response).jsonObject["mode"] as? JsonPrimitive)?.content }.getOrNull()
            showReply(text, panelFor(response))
            if (mode == null || mode == "voice") voice.say(text)
            // 응답 화면이 있으면 5초 머문다(가이드 Standby). 읽을 시간을 조금 더 둔다.
            main.postDelayed(::sleep, STANDBY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        voice = SimVoice(this).also { it.start() }
        windows = getSystemService(WindowManager::class.java)
        // 안전벨트 알림 덮개. Gleo 빛과 말풍선보다 아래에 깔리도록 먼저 붙인다. 눌리지 않는 창은 80% 불투명도로 제한되므로 눌리는 창으로 둔다.
        cover = View(this).apply { setBackgroundColor(Color.WHITE) }
        windows.addView(cover, params(dp(BELT_COVER.w), dp(BELT_COVER.h), dp(BELT_COVER.x), dp(BELT_COVER.y), touchable = true))
        chrome = ChromeView(this).apply { visibility = View.GONE }
        windows.addView(chrome, params(dp(CHROME_WIDTH), dp(CHROME_HEIGHT), dp(PANEL_X + PANEL_WIDTH / 2 - CHROME_WIDTH / 2), 0, touchable = false))
        stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        bubble = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.parseColor("#20245C"))
            background = rounded(Color.parseColor("#D9B8BCF6"), 40)
            setPadding(dp(28), dp(12), dp(28), dp(12))
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 32)
            elevation = dp(16).toFloat()
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        stack.addView(bubble, LinearLayout.LayoutParams(-2, -2))
        stack.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        stack.setPadding(dp(24), dp(8), dp(24), dp(32))
        windows.addView(stack, params(dp(PANEL_WIDTH), WindowManager.LayoutParams.WRAP_CONTENT, dp(PANEL_X), dp(PANEL_Y), touchable = true))
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(responses, IntentFilter(RESPONSE_ACTION), RECEIVER_EXPORTED)
            registerReceiver(says, IntentFilter(SAY_ACTION), RECEIVER_EXPORTED)
        } else {
            registerReceiver(responses, IntentFilter(RESPONSE_ACTION))
            registerReceiver(says, IntentFilter(SAY_ACTION))
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        unregisterReceiver(responses)
        unregisterReceiver(says)
        windows.removeView(cover)
        windows.removeView(chrome)
        windows.removeView(stack)
        voice.release()
        super.onDestroy()
    }

    /** 가이드의 상태 순서: 듣는 중(대기) → 듣는 중(말함) → 생각 중 → 말하는 중 → 잠시 머문 뒤 잠듦. */
    private fun converse(scenario: Scenario) {
        main.removeCallbacksAndMessages(null)
        waiting = false
        chrome.status = ChromeView.Status.LISTENING
        chrome.visibility = View.VISIBLE
        stack.visibility = View.VISIBLE
        panel.visibility = View.GONE
        bubble.text = "운전석에서 듣는 중..."
        main.postDelayed({ bubble.text = scenario.utterance }, LISTEN_MS)
        main.postDelayed({
            chrome.status = ChromeView.Status.THINKING
            waiting = true
            Log.i(TAG, "send ${scenario.function} ${scenario.paramJson}")
            sendBroadcast(Intent(scenario.function).setPackage(BYWAY).putExtra("ACTION_PARAM", scenario.paramJson))
            main.postDelayed(noAnswer, NO_ANSWER_MS)
        }, LISTEN_MS + SPOKEN_MS)
    }

    private fun showReply(text: String, content: Panel?) {
        chrome.status = ChromeView.Status.SPEAKING
        bubble.text = text
        panel.removeAllViews()
        content?.items?.forEachIndexed { i, item ->
            panel.addView(row(item), LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(10) })
        }
        panel.visibility = if (content == null) View.GONE else View.VISIBLE
    }

    private fun sleep() {
        chrome.visibility = View.GONE
        stack.visibility = View.GONE
    }

    /** 목록 한 줄(가이드 List Type): 번호 동그라미, 굵은 제목, 설명 줄, 회색 태그. */
    private fun row(item: PanelItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(Color.parseColor("#F3F4FB"), 20)
        setPadding(dp(18), dp(14), dp(18), dp(14))
        item.number?.let { n ->
            addView(TextView(context).apply {
                text = "$n"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(BLUE)
                }
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                topMargin = dp(4)
                marginEnd = dp(14)
            })
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(item.title, 30f, Color.parseColor("#111111"), bold = true))
            if (item.meta.isNotEmpty()) addView(text(item.meta, 20f, GRAY), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
            if (item.tags.isNotEmpty()) {
                addView(LinearLayout(context).apply {
                    item.tags.forEach { tag ->
                        addView(text(tag, 18f, GRAY).apply {
                            background = rounded(Color.parseColor("#E8EAEF"), 10)
                            setPadding(dp(10), dp(3), dp(10), dp(3))
                        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
    }

    /**
     * 겹창. 안드로이드 12+ 는 다른 앱의 눌리지 않는 겹창을 80% 불투명도로 제한하므로, 흰 패널은 눌리는 창으로 둔다
     * (실제 Gleo 패널도 눌린다). 크롬 빛은 반투명이라 눌리지 않게 둔다.
     */
    private fun params(w: Int, h: Int, x: Int, y: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 화면 뒤로 가도 Gleo 화면과 Callback 수신이 살아 있게 포그라운드 서비스로 둔다. */
    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Gleo 시연", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Gleo 시연 화면 실행 중")
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, notification)
    }

    private companion object {
        const val TAG = "GleoSim"
        const val BYWAY = "com.byway"
        const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        const val SAY_ACTION = "com.byway.gleosim.SAY"
        const val CHANNEL = "gleosim"
        const val NO_ANSWER_MS = 7_000L
        const val LISTEN_MS = 900L
        const val SPOKEN_MS = 1_100L
        const val STANDBY_MS = 6_000L
        const val CHROME_WIDTH = 900
        const val CHROME_HEIGHT = 170
        const val PANEL_WIDTH = 760
        const val PANEL_X = 60
        const val PANEL_Y = 70
        val BLUE = Color.parseColor("#5B66F5")
        val GRAY = Color.parseColor("#5F6368")
    }
}
