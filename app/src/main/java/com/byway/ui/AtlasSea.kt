package com.byway.ui

/**
 * 한국 지도 그림의 바다색. 그림 바깥을 이 색으로 칠해, 네 변을 녹여도 바다에서 바다로 넘어가 사각 틀이 생기지 않는다(화면 다듬기 설계 3절).
 * 값은 assets/atlas 의 각 그림 왼쪽 가장자리(바다) 화소에서 떴다. 그림을 다시 만들면 다시 뜬다.
 */
fun seaColor(style: MapStyle, night: Boolean): Int = when (style) {
    MapStyle.MAP -> if (night) 0xFF131419.toInt() else 0xFF91A3AF.toInt()
    MapStyle.SATELLITE -> if (night) 0xFF0A1930.toInt() else 0xFF182B4C.toInt()
}
