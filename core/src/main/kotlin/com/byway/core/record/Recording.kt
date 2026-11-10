package com.byway.core.record

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint

/** 달린 길을 코스로 만들 때의 값. 공유 설계 6-3. */
data class RecordRules(
    /** 집 위치가 드러나지 않게 처음과 끝에서 잘라 내는 거리. */
    val trimMeters: Double = 500.0,
    val minMeters: Double = 5_000.0,
    val sectionMeters: Double = 5_000.0,
    /** 이웃한 위치가 이보다 멀면 그 사이 위치가 끊긴 것이다(터널 등). 달린 거리에 넣지 않는다. */
    val jumpMeters: Double = 500.0,
    /** 1초마다 온 위치를 이 간격으로 솎는다. */
    val pointMeters: Double = 50.0,
)

sealed interface Recorded {
    data class Made(val course: Course) : Recorded
    data class TooShort(val meters: Double) : Recorded
    data class BadName(val reason: String) : Recorded
}

/** 코스 분위기. 차 안 연출과 음악은 구간의 이 값으로 움직인다. */
val RECORD_MOODS = listOf("forest", "city", "river")

const val DRIVER_ATTRIBUTION = "에움길 운전자 기록"
private const val NAME_MAX = 20

/** 다른 사람에게 보이는 이름이라 대표적인 욕설과 광고 연락처 표현만 막는다. */
private val BANNED = listOf("시발", "씨발", "개새끼", "병신", "좆", "카톡", "텔레그램", "http", "www.", "오픈채팅")

fun nameProblem(name: String): String? {
    val clean = name.trim()
    return when {
        clean.isEmpty() -> "이름을 넣어 주세요"
        clean.length > NAME_MAX -> "${NAME_MAX}자 안으로 줄여 주세요"
        BANNED.any { it in clean.lowercase() } -> "쓸 수 없는 말이 들어 있습니다"
        else -> null
    }
}

/** 길을 따라 처음과 끝에서 [meters]씩 안쪽에 있는 위치만 남긴다. */
fun trimEnds(path: List<GeoPoint>, meters: Double): List<GeoPoint> {
    val along = alongMeters(path)
    val total = along.lastOrNull() ?: return emptyList()
    return path.filterIndexed { i, _ -> along[i] >= meters && total - along[i] >= meters }
}

/** 달린 거리. 끊겼다 튄 사이는 넣지 않는다. */
fun drivenMeters(path: List<GeoPoint>, jumpMeters: Double): Double =
    path.zipWithNext { a, b -> a.distanceTo(b) }.filter { it <= jumpMeters }.sum()

fun makeCourse(id: String, name: String, mood: String, fixes: List<GeoPoint>, rules: RecordRules = RecordRules()): Recorded {
    nameProblem(name)?.let { return Recorded.BadName(it) }
    require(mood in RECORD_MOODS) { "모르는 분위기: $mood" }
    val trimmed = trimEnds(fixes, rules.trimMeters)
    val meters = drivenMeters(trimmed, rules.jumpMeters)
    if (meters < rules.minMeters) return Recorded.TooShort(meters)
    val path = thin(trimmed, rules.pointMeters)
    val sections = sectionsOf(path, rules.sectionMeters).mapIndexed { i, (start, end) -> Section("${i + 1}구간", start, end, mood) }
    return Recorded.Made(Course(id = id, name = name.trim(), path = path, sections = sections, attribution = DRIVER_ATTRIBUTION))
}

private fun alongMeters(path: List<GeoPoint>): List<Double> =
    path.indices.runningFold(0.0) { sum, i -> if (i == 0) sum else sum + path[i - 1].distanceTo(path[i]) }.drop(1)

/** 앞서 남긴 점에서 [meters] 넘게 떨어진 점만 남기고, 마지막 점은 늘 남긴다. */
private fun thin(path: List<GeoPoint>, meters: Double): List<GeoPoint> {
    val kept = mutableListOf(path.first())
    path.drop(1).dropLast(1).forEach { if (it.distanceTo(kept.last()) >= meters) kept += it }
    kept += path.last()
    return kept
}

/** 약 [meters]마다 나눈 (시작, 끝) 지점 번호. 끝에 남는 짧은 토막은 앞 구간에 붙인다. */
private fun sectionsOf(path: List<GeoPoint>, meters: Double): List<Pair<Int, Int>> {
    val along = alongMeters(path)
    val total = along.last()
    val ends = mutableListOf<Int>()
    var next = meters
    along.forEachIndexed { i, m ->
        if (m >= next && total - m >= meters / 2 && i < path.lastIndex) {
            ends += i
            next = m + meters
        }
    }
    ends += path.lastIndex
    return ends.mapIndexed { k, end -> (if (k == 0) 0 else ends[k - 1] + 1) to end }
}
