package com.byway.platform

import com.byway.core.geo.GeoPoint

/**
 * 내비의 경로가 우리가 올린 것인지 가린다. 에뮬레이터 실측(2026-11-09)에 맞춘 규칙이다.
 * - 내비는 새 경로를 받으면 앞 경로가 꺼졌다고 먼저 알리고 몇 초 뒤에 시작을 알린다. 우리 요청이든 운전자 조작이든 같다.
 *   그래서 우리 요청의 시작을 기다리는 동안 온 "꺼짐"은 우리가 밀어낸 것으로 본다.
 * - 시작 이벤트에는 좌표가 없다. 시작 뒤 목적지를 물어 받은 좌표를 우리가 보낸 좌표와 견준다.
 *   내비가 좌표를 도로에 맞춰 옮길 수 있어 같은지가 아니라 거리로 본다.
 * - 요청을 잇달아 보내면 앞 요청의 시작이 뒤 요청의 취소보다 먼저 올 수 있다. 그래서 기다림은 마지막 요청의 좌표가 확인될 때만 끝난다.
 */
class RouteLedger(
    private val nearMeters: Double = 200.0,
    private val keep: Int = 3,
    private val startWaitMs: Long = 30_000,
    private val arriveGraceMs: Long = 10_000,
) {
    private val sent = ArrayDeque<GeoPoint>()
    private var waitingSince: Long? = null
    private var ownerBefore = false
    private var retried = false
    private var arrivedAt: Long? = null

    /** 지금 내비에 떠 있는 경로가 우리가 올린 것인지. 끝낼 때 우리 경로만 지우려고 본다. */
    var oursIsActive = false
        private set

    /** 우리가 경로를 올렸다. */
    fun requested(destination: GeoPoint, nowMs: Long) {
        if (waitingSince == null) ownerBefore = oursIsActive
        sent.addLast(destination)
        if (sent.size > keep) sent.removeFirst()
        waitingSince = nowMs
        oursIsActive = true
    }

    /** 우리 요청이 내비에서 실패했다. 지금 경로의 주인은 요청하기 전과 같다. 한 번은 다시 시도할 만하면 true 다. */
    fun failed(): Boolean {
        waitingSince = null
        oursIsActive = ownerBefore
        return !retried.also { retried = true }
    }

    /** 우리 경로의 목적지에 닿았다. 곧 올 "꺼짐"은 운전자가 끈 것이 아니다. */
    fun arrived(nowMs: Long) {
        arrivedAt = nowMs
    }

    /**
     * 내비가 경로가 꺼졌다고 알렸다. 우리 요청이 앞 경로를 밀어냈거나 우리 목적지에 닿아 끝난 것이면 true 다.
     * false 면 운전자가 내비를 직접 만진 것이다.
     */
    fun cancelIsOurs(nowMs: Long): Boolean {
        val replacing = waitingSince?.let { nowMs - it in 0..startWaitMs } == true
        val finished = arrivedAt?.let { nowMs - it in 0..arriveGraceMs } == true
        arrivedAt = null
        if (!replacing) oursIsActive = false
        return replacing || finished
    }

    /** 내비가 알려 온 목적지. 우리가 보낸 것이면 true 다. */
    fun seen(destination: GeoPoint): Boolean {
        oursIsActive = sent.any { it.distanceTo(destination) <= nearMeters }
        if (sent.lastOrNull()?.let { it.distanceTo(destination) <= nearMeters } == true) {
            waitingSince = null
            retried = false
        }
        return oursIsActive
    }
}
