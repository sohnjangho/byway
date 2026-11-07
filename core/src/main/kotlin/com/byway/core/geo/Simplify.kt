package com.byway.core.geo

/**
 * 코스에서 직선으로 이어도 벗어나지 않는 점을 버리고 꺾이는 지점만 남긴다(Douglas-Peucker).
 * 남은 지점이 내비에 올릴 경유지 후보다.
 */
fun keyPointIndexes(path: List<GeoPoint>, toleranceMeters: Double): List<Int> {
    if (path.size < 2) return path.indices.toList()
    val kept = sortedSetOf(0, path.lastIndex)
    val pending = ArrayDeque(listOf(0 to path.lastIndex))
    while (pending.isNotEmpty()) {
        val (first, last) = pending.removeLast()
        var farthest = -1
        var farthestDistance = toleranceMeters
        for (i in first + 1 until last) {
            val distance = path[i].distanceToSegment(path[first], path[last])
            if (distance > farthestDistance) {
                farthest = i
                farthestDistance = distance
            }
        }
        if (farthest >= 0) {
            kept += farthest
            pending += first to farthest
            pending += farthest to last
        }
    }
    return kept.toList()
}
