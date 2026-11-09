package com.byway.core.engine

internal object Messages {
    fun courseStart(name: String) = "$name 코스를 시작합니다."
    fun courseResumed(name: String) = "$name 코스를 이어갑니다."
    fun sectionEntered(name: String) = "$name 구간입니다."
    fun skipped(names: List<String>) = "${names.joinToString(", ")} 구간을 건너뛰었습니다."
    const val NEXT_LEG = "다음 구간으로 이어갑니다."
    const val TO_START = "코스 출발점으로 안내합니다."
    const val JOINED_MIDWAY = "코스 중간에서 시작합니다."
    const val ASK_DETOUR = "코스를 벗어나고 있습니다. 잠깐 들르시나요?"
    const val DETOUR_OK = "돌아오면 이어서 안내합니다."
    const val REJOINING = "코스로 돌아가는 길을 안내합니다."
    const val REJOINED = "코스로 돌아왔습니다."
    const val FREE = "내비 경로로 진행합니다."
    const val PAUSED = "코스를 잠시 멈춥니다. 돌아오면 이어갑니다."
    const val ASK_RESUME = "코스를 이어갈까요?"
    const val RESUMING = "코스를 이어갑니다."
    const val FINISHED = "코스를 끝까지 달렸습니다."
    const val START_POINT = "코스 출발점"
    const val REJOIN_POINT = "코스 복귀 지점"
}
