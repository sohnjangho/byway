package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseFacts
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked
import com.byway.core.course.SceneMood
import com.byway.core.course.Shelf
import com.byway.core.course.Stop
import com.byway.core.course.characterOf
import com.byway.core.course.factsOf
import com.byway.core.course.lengthScale
import com.byway.core.course.rank
import com.byway.core.course.regions
import com.byway.core.course.suggestFor
import com.byway.core.course.themes

/** 코스별 수치를 한 번만 계산해 둔다. 화면은 다시 계산하지 않고 여기서 읽는다. */
class CourseBook(val courses: List<Course>) {
    private val byId = courses.associateBy { it.id }

    /** 경로를 잰 실제 코스. 더미 코스는 랭킹, 자동 추천, 홈 띠에 넣지 않는다(더 많은 코스 설계 8절). */
    private val real = courses.filterNot { it.dummy }
    private val rankings = mutableMapOf<RankBy, List<Ranked>>()
    private val measured by lazy {
        ranked(RankBy.CURVES).associate { it.course.id to it.facts } + courses.filter { it.dummy }.associate { it.id to factsOf(it) }
    }
    private val characters = mutableMapOf<String, String>()
    private val suggestions = mutableMapOf<Int, Course>()
    private val moodSuggestions = mutableMapOf<Pair<SceneMood, Int>, Course>()
    private val lengths = mutableMapOf<String, List<Double>>()
    private val stops = mutableMapOf<String, List<Stop>>()

    /** 화면 문구가 없는 테마는 타일로 만들지 않는다. */
    val themes: List<Shelf> = themes(courses).filter { themeLabel(it.key) != null }
    val regions: List<Shelf> = regions(courses)

    /**
     * 특색을 견줄 상대. characterOf 는 부를 때마다 받은 코스를 모두 다시 재므로 전체를 넘기면 코스 수의 제곱이 된다.
     * 그 규칙은 수치마다 혼자 앞서는지만 보니 수치별 1·2위만 넘겨도 답이 같다. 1위가 동점이면 혼자 앞서는 것이 아니므로 2위까지 둔다.
     */
    internal val rivals: List<Course> by lazy {
        listOf<(CourseFacts) -> Double>({ it.curves.toDouble() }, { it.eumRatio }, { it.meters }, { it.climbMeters.toDouble() })
            .flatMap { measure -> real.sortedByDescending { measure(facts(it)) }.take(2) }.distinctBy { it.id }
    }

    fun find(id: String): Course = byId.getValue(id)

    /** 기록에 남은 코스가 앱에서 사라졌으면 뺀다. */
    fun known(ids: Collection<String>): List<Course> = ids.mapNotNull(byId::get)

    /** 달린 코스를 앞에, 저장한 코스를 뒤에 둔다. 앱에서 사라진 코스는 뺀다. */
    fun mine(saved: Set<String>, recent: List<String>): List<Course> = known((recent + saved).distinct())

    fun facts(course: Course): CourseFacts = measured.getValue(course.id)

    fun ranked(by: RankBy): List<Ranked> = rankings.getOrPut(by) { rank(real, by) }

    fun character(course: Course): String = characters.getOrPut(course.id) {
        characterOf(course, (rivals + course).distinctBy { it.id })
    }

    fun suggested(hour: Int): Course = suggestions.getOrPut(hour) { suggestFor(real, hour) }

    fun suggested(mood: SceneMood, hour: Int): Course = moodSuggestions.getOrPut(mood to hour) { suggestFor(real, mood, hour) }

    /** 홈 띠와 다른 코스 서랍에 놓는 코스. 실제 코스에, 지금 고른 코스가 더미면 그 코스를 맨 앞에 더한다. */
    fun featured(currentId: String?): List<Course> =
        listOfNotNull(currentId?.let(byId::get)?.takeIf { it.dummy }) + real

    fun sectionMeters(course: Course): List<Double> = lengths.getOrPut(course.id) { sectionLengths(course) }

    fun stops(course: Course): List<Stop> = stops.getOrPut(course.id) { naviStops(course) }

    /** 구간 끝과 다음 구간 시작 사이의 한 칸은 앞 구간에 넣어 합이 코스 길이가 되게 한다. 더미 코스는 표기 거리로 늘린다. */
    private fun sectionLengths(course: Course): List<Double> {
        val scale = course.lengthScale()
        return course.sections.map { section ->
            val end = minOf(section.endIndex + 1, course.path.lastIndex)
            course.path.subList(section.startIndex, end + 1).zipWithNext { a, b -> a.distanceTo(b) }.sum() * scale
        }
    }
}
