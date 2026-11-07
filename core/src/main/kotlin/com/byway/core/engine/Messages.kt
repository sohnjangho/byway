package com.byway.core.engine

internal object Messages {
    fun courseStart(name: String) = "$name 코스를 시작합니다."
    fun sectionEntered(name: String) = "$name 구간입니다."
    const val NEXT_LEG = "다음 구간으로 이어갑니다."
    const val REJOINING = "코스를 벗어났습니다. 코스로 돌아가는 길을 안내합니다."
    const val REJOINED = "코스로 돌아왔습니다."
    const val FINISHED = "코스를 끝까지 달렸습니다."
    const val REJOIN_POINT = "코스 복귀 지점"
    fun line(sectionName: String, remainingMeters: Double) =
        "$sectionName · 남은 거리 ${"%.1f".format(java.util.Locale.ROOT, remainingMeters / 1_000)}km"
}
