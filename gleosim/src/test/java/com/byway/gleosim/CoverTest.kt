package com.byway.gleosim

import org.junit.Assert.assertTrue
import org.junit.Test

/** 왼쪽 차량 정보 칸의 안전벨트 그림과 "안전 벨트를 매세요." 글자를 덮는다(PM 11-13 영상 피드백). */
class CoverTest {
    @Test
    fun coverHidesSeatBeltReminder() {
        // 에뮬레이터 2560x1440(밀도 160)에서 잰 자리: 그림 x 300~566, y 83~241, 글자 x 335~518, y 257~277
        assertTrue(BELT_COVER.contains(300, 83, 566, 277))
    }

    @Test
    fun coverStaysInsideVehiclePanel() {
        // 차량 정보 칸(x 0~853)의 위쪽 줄(거리, 속도, 기어, y 21~68)은 가리지 않는다.
        assertTrue(BELT_COVER.x >= 0 && BELT_COVER.x + BELT_COVER.w <= 853)
        assertTrue(BELT_COVER.y > 68)
    }
}
