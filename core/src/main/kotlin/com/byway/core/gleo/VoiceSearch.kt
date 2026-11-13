package com.byway.core.gleo

import com.byway.core.course.Course
import com.byway.core.course.CourseFilter
import com.byway.core.course.Found
import com.byway.core.course.NearRange
import com.byway.core.course.filterCourses
import com.byway.core.course.rank
import com.byway.core.geo.GeoPoint

/** [GleoNames.FIND]의 인자(설계 3-1). [mood]·[length]·[rankBy]는 스키마의 영어 enum 값이다. 모르는 값은 그 조건만 건너뛴다. */
data class VoiceQuery(
    val searchTerm: String = "",
    val mood: String? = null,
    val nearbyKm: Double? = null,
    val length: String? = null,
    val region: String? = null,
    val rankBy: String? = null,
    val myCoursesOnly: Boolean = false,
)

fun VoiceQuery.isEmpty(): Boolean = this == VoiceQuery(searchTerm = searchTerm) && searchTerm.isBlank()

/** 말한 km 를 화면의 내 주변 칩(25·50·75)으로 올린다. 75 를 넘으면 75 다. */
fun nearRangeFor(km: Double): NearRange = NearRange.entries.firstOrNull { km <= it.km } ?: NearRange.KM75

/** [top]은 Gleo 에 넘길 앞의 [VOICE_TOP]개다. [positionUnknown]은 내 주변을 물었는데 위치를 몰라 건너뛴 경우다. */
data class VoiceHits(val total: Int, val top: List<Found>, val positionUnknown: Boolean)

const val VOICE_TOP = 3

/**
 * 음성 찾기. 거르기는 더 많은 코스와 같은 [filterCourses]다.
 * 랭킹 기준이 있으면 랭킹 화면처럼 더미를 빼고 그 순서로, 없으면 실제 코스가 먼저·더미가 나중이고 각각 가까운 순이다.
 */
fun findForVoice(
    courses: List<Course>,
    query: VoiceQuery,
    here: GeoPoint?,
    mine: Set<String>,
    metersOf: (Course) -> Double,
    wordsOf: (Course) -> List<String>,
): VoiceHits {
    val by = query.rankBy?.let(RANK_ENUM::get)
    val pool = courses
        .filter { !query.myCoursesOnly || it.id in mine }
        .filter { by == null || !it.dummy }
    val filter = CourseFilter(
        query = query.searchTerm,
        near = query.nearbyKm?.let(::nearRangeFor),
        mood = query.mood?.let(MOOD_ENUM::get),
        length = query.length?.let(LENGTH_ENUM::get),
        region = query.region?.takeIf { it.isNotBlank() },
    )
    val found = filterCourses(pool, filter, here, metersOf, wordsOf)
    val ordered = if (by != null) {
        val order = rank(found.map { it.course }, by).mapIndexed { i, ranked -> ranked.course.id to i }.toMap()
        found.sortedBy { order.getValue(it.course.id) }
    } else {
        found.sortedWith(compareBy<Found> { it.course.dummy }.thenBy { it.awayMeters ?: 0.0 })
    }
    return VoiceHits(ordered.size, ordered.take(VOICE_TOP), query.nearbyKm != null && here == null)
}

/** 이름 맞추기 결과. [Many]의 후보는 최대 [VOICE_TOP]개다. */
sealed interface NameMatch {
    data class One(val course: Course) : NameMatch
    data class Many(val candidates: List<Course>) : NameMatch
    data object None : NameMatch
}

/**
 * 말한 코스 이름을 코스 하나로 정한다(설계 3-2). 차례로 1) 공백·대소문자를 빼고 같은 이름, 2) 이름에 든 말,
 * 3) 모든 낱말이 이름·지역·[wordsOf]에 듦을 보고, 처음 결과가 나온 단계에서 멈춘다.
 */
fun matchCourse(courses: List<Course>, name: String, wordsOf: (Course) -> List<String>): NameMatch {
    val said = squash(name)
    if (said.isEmpty()) return NameMatch.None
    val words = name.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val steps = listOf<(Course) -> Boolean>(
        { squash(it.name) == said },
        { said in squash(it.name) },
        { course -> (listOf(course.name, course.region) + wordsOf(course)).joinToString(" ").lowercase().let { text -> words.all(text::contains) } },
    )
    for (step in steps) {
        val hits = courses.filter(step)
        when {
            hits.size == 1 -> return NameMatch.One(hits.single())
            hits.size > 1 -> return NameMatch.Many(hits.take(VOICE_TOP))
        }
    }
    return NameMatch.None
}

private fun squash(text: String): String = text.lowercase().filterNot { it.isWhitespace() }
