package com.byway.gleosim

/** 화면 위 네모(dp). 에뮬레이터는 밀도 160이라 dp와 화소가 같다. */
data class Box(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun contains(left: Int, top: Int, right: Int, bottom: Int) = x <= left && y <= top && x + w >= right && y + h >= bottom
}

/**
 * 왼쪽 차량 정보 칸의 안전벨트 알림(그림과 "안전 벨트를 매세요.")을 덮는 흰 네모(PM 11-13 영상 피드백).
 * 에뮬레이터는 user 빌드라 벨트를 맨 상태로 바꿀 수 없어서, 시연 화면이 같은 흰색으로 덮는다.
 */
val BELT_COVER = Box(x = 270, y = 74, w = 340, h = 220)
