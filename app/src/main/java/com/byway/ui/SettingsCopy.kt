package com.byway.ui

private const val CLEAR_ARMED = 1
private const val CLEAR_DONE = 2

/** 내 기록 지우기 버튼의 문구. 대화상자 없이 두 번 눌러야 지워진다. 반 칸 버튼에 한 줄로 들어가는 길이로 쓴다. */
fun clearLabel(step: Int): String = when (step) {
    CLEAR_ARMED -> "다시 눌러 지우기"
    CLEAR_DONE -> "지웠습니다"
    else -> "내 기록 지우기"
}

fun clearsOnPress(step: Int): Boolean = step == CLEAR_ARMED

fun nextClearStep(step: Int): Int = minOf(step + 1, CLEAR_DONE)
