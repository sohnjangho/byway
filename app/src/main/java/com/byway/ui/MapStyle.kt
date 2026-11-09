package com.byway.ui

import com.byway.service.MyDrives

/** 지도 그림의 종류. 일반 지도가 기본이고 위성은 설정에서 고른다. [suffix]는 그림 파일 이름에 붙는다. */
enum class MapStyle(val suffix: String, val title: String) {
    MAP("-map", "일반 지도"),
    SATELLITE("", "위성");

    companion object {
        fun of(key: String?): MapStyle = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: MAP
    }
}

internal fun currentMapStyle(): MapStyle = MapStyle.of(MyDrives.mapStyle)

/** 지도 위 번호 표시를 어둡게 그릴지. 밝은 일반 지도에서는 흰 원이 묻힌다. */
fun darkPinsOn(style: MapStyle, night: Boolean): Boolean = style == MapStyle.MAP && !night
