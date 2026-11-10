package com.byway.server

import com.byway.core.geo.GeoPoint
import java.util.concurrent.ConcurrentHashMap

/** 파티 멤버 위치. DB에 쓰지 않고 메모리에만 두며 60초가 지나면 없는 것으로 본다(공유 설계 6-1). */
class Positions(private val clock: () -> Long, private val ttlMs: Long = 60_000L) {
    private data class Fix(val point: GeoPoint, val at: Long)

    private val fixes = ConcurrentHashMap<Pair<String, String>, Fix>()

    fun put(party: String, device: String, point: GeoPoint) {
        fixes[party to device] = Fix(point, clock())
    }

    /** 살아 있는 위치. 가장 최근 것이 앞에 온다. 지난 것은 이때 지운다. */
    fun live(party: String): List<Pair<String, GeoPoint>> {
        val now = clock()
        fixes.entries.removeIf { now - it.value.at > ttlMs }
        return fixes.filterKeys { it.first == party }.entries.sortedByDescending { it.value.at }.map { it.key.second to it.value.point }
    }

    fun remove(party: String, device: String) {
        fixes.remove(party to device)
    }

    fun clearParty(party: String) {
        fixes.keys.removeIf { it.first == party }
    }

    fun forget(device: String) {
        fixes.keys.removeIf { it.second == device }
    }
}
