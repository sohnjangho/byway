package com.byway.service

import android.app.Activity
import android.os.Bundle

/**
 * Gleo 의 출발(START_DRIVING_COURSE)을 받는 투명한 화면. schema 에서 출발만 component 가 activity 다(설계 3-2, 11-14).
 * 안드로이드 12+ 는 화면 뒤에 있는 앱의 포그라운드 서비스 시작을 막는다. 이 화면이 떠 있는 동안에는 앱이 보이는 상태라
 * 에움길이 내비 뒤에 가려져 있어도 출발할 수 있다. 그림은 없고, 답을 보내면 바로 닫는다.
 */
class GleoStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GleoReceiver().handle(this, intent) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
