package com.byway.service

/** 직전 음성 찾기 결과(코스 id). "1번으로 가자"를 받으려고 10분 동안 기억한다(설계 3-1). */
object GleoMemory {
    private const val KEEP_MS = 10 * 60 * 1_000L
    private var ids: List<String> = emptyList()
    private var at = 0L

    @Synchronized
    fun remember(ids: List<String>, nowMs: Long) {
        this.ids = ids
        at = nowMs
    }

    @Synchronized
    fun recall(nowMs: Long): List<String> = if (nowMs - at <= KEEP_MS) ids else emptyList()
}
