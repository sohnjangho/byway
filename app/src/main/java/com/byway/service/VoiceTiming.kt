package com.byway.service

/** 경로를 올린 직후에는 내비가 새 경로를 말한다. 우리 안내는 그 뒤로 미룬다. */
const val ROUTE_VOICE_GAP_MS = 2_500L

/** [sinceRouteMs]는 마지막으로 경로를 올린 뒤 지난 시간이다. 올린 적이 없으면 null 이다. */
fun speechDelayMs(sinceRouteMs: Long?): Long =
    if (sinceRouteMs == null) 0L else (ROUTE_VOICE_GAP_MS - sinceRouteMs).coerceIn(0L, ROUTE_VOICE_GAP_MS)

const val STALE_FIX_MS = 10_000L

/** 위치 시각이 멈춘 지 오래면 위치를 놓친 것이다. 서 있을 때는 따지지 않는다. */
fun isLocationStale(fixAgeMs: Long, parked: Boolean): Boolean = !parked && fixAgeMs >= STALE_FIX_MS
