package com.byway.gleosim

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView

/** 대화창을 띄우고 바로 닫힌다. 다른 앱 위에 그리기 권한이 없으면 adb 로 주는 방법을 보인다. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            setContentView(TextView(this).apply {
                text = "다른 앱 위에 그리기 권한이 필요합니다.\nadb shell appops set com.byway.gleosim SYSTEM_ALERT_WINDOW allow"
                textSize = 20f
                setPadding(48, 48, 48, 48)
            })
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        finish()
    }
}
