package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseFacts
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked
import com.byway.core.course.SceneMood
import com.byway.core.course.Shelf
import com.byway.core.course.Stop
import com.byway.core.course.characterOf
import com.byway.core.course.rank
import com.byway.core.course.regions
import com.byway.core.course.suggestFor
import com.byway.core.course.themes

/** 코스별 수치를 한 번만 계산해 둔다. 화면은 다시 계산하지 않고 여기서 읽는다. */
class CourseBook(val courses: List<Course>) {
    private val byId = courses.associateBy { it.id }
    private val rankings = mutableMapOf<RankBy, List<Ranked>>()
    private val measured by lazy { ranked(RankBy.CURVES).associate { it.course.id to it.facts } }
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
        RankBy.entries.flatMap { by -> ranked(by).take(2).map { it.course } }.distinctBy { it.id }
    }

    fun find(id: String): Course = byId.getValue(id)

    /** 기록에 남은 코스가 앱에서 사라졌으면 뺀다. */
    fun known(ids: Collection<String>): List<Course> = ids.mapNotNull(byId::get)

    fun facts(course: Course): CourseFacts = measured.getValue(course.id)

    fun ranked(by: RankBy): List<Ranked> = rankings.getOrPut(by) { rank(courses, by) }

    fun character(course: Course): String = characters.getOrPut(course.id) {
        characterOf(course, (rivals + course).distinctBy { it.id })
    }

    fun suggested(hour: Int): Course = suggestions.getOrPut(hour) { suggestFor(courses, hour) }

    fun suggested(mood: SceneMood, hour: Int): Course = moodSuggestions.getOrPut(mood to hour) { suggestFor(courses, mood, hour) }

    fun sectionMeters(course: Course): List<Double> = lengths.getOrPut(course.id) { sectionLengths(course) }

    fun stops(course: Course): List<Stop> = stops.getOrPut(course.id) { naviStops(course) }

    /** 구간 끝과 다음 구간 시작 사이의 한 칸은 앞 구간에 넣어 합이 코스 길이가 되게 한다. */
    private fun sectionLengths(course: Course): List<Double> = course.sections.map { section ->
        val end = minOf(section.endIndex + 1, course.path.lastIndex)
        course.path.subList(section.startIndex, end + 1).zipWithNext { a, b -> a.distanceTo(b) }.sum()
    }
}
