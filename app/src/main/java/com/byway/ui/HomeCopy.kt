package com.byway.ui

import com.byway.core.course.Course
import com.byway.core.course.CourseFacts
import com.byway.core.course.EnergyRate
import com.byway.core.course.SceneMood
import com.byway.core.course.Pace
import com.byway.core.course.ScenePlace
import com.byway.core.course.energyNeeded
import com.byway.core.course.funScore
import com.byway.core.course.seasonFit
import com.byway.core.course.sunTimes
import com.byway.core.course.timeFit
import java.util.Locale

/** 홈 첫 줄. 시안 Main 의 문구와 같다. */
fun kickFor(mood: SceneMood): String = when (mood) {
    SceneMood.PLAIN -> "오늘 달리기 좋은 길"
    SceneMood.RAIN -> "비 오는 날 호숫가로 가는 길"
    SceneMood.SNOW -> "눈 오는 날, 오르막이 가장 적은 길"
    SceneMood.FOG -> "안개 낀 날, 오르막이 가장 적은 길"
    SceneMood.CLOUDY -> "구름 낀 날 바다를 끼고 달리는 길"
    SceneMood.SUNRISE -> "해 뜨는 시간에 어울리는 길"
    SceneMood.SUNSET -> "노을이 지는 시간에 어울리는 길"
    SceneMood.NIGHT -> "밤에 달리기 좋은 길"
    SceneMood.SPRING -> "봄에 달리기 좋은 길"
    SceneMood.SUMMER -> "여름에 달리기 좋은 길"
    SceneMood.AUTUMN -> "단풍철에 달리기 좋은 길"
    SceneMood.WINTER -> "겨울에 달리기 좋은 길"
}

private const val PER_KIND = 2

/** 코스 주변 목록. 경치, 명소, 음식을 종류마다 둘까지 두고 번호를 다시 매긴다. */
fun shownPlaces(places: List<ScenePlace>): List<ScenePlace> = kept(places).mapIndexed { i, place -> place.copy(no = i + 1) }

/**
 * 도구가 매긴 장소 번호에서 목록에 보이는 번호로 가는 대응. 목록에서 빠진 장소는 없다.
 * 훑어보기 자료는 도구의 번호로 장소를 가리키므로, 번호 표시와 "근처" 글은 이 대응을 거친다.
 */
fun shownNumbers(places: List<ScenePlace>): Map<Int, Int> = kept(places).mapIndexed { i, place -> place.no to i + 1 }.toMap()

private fun kept(places: List<ScenePlace>): List<ScenePlace> {
    val used = mutableMapOf<String, Int>()
    return places.filter { place ->
        val kind = if (place.kind == "photo") "view" else place.kind
        val count = used.getOrDefault(kind, 0)
        used[kind] = count + 1
        count < PER_KIND
    }
}

fun placeLine(place: ScenePlace): String {
    val km = if (place.km % 1.0 == 0.0) "${place.km.toInt()}" else "${place.km}"
    return listOf(place.label, place.note, "${km}km 지점").filter { it.isNotBlank() }.joinToString(" · ")
}

/** 고른 카드가 이미 보이면 띠를 그대로 두어, 누른 자리 밑에서 띠가 움직이지 않게 한다. */
fun stripScrollFor(previous: Int?, index: Int, step: Int, cardWidth: Int, viewWidth: Int): Int {
    val left = index * step
    if (previous != null && left >= previous && left + cardWidth <= previous + viewWidth) return previous
    return left
}

/** 출처가 없는 사진은 보이지 않는다. */
fun canSwipeToScenery(credit: String, hasFile: Boolean): Boolean = credit.isNotBlank() && hasFile

/** 풍경 사진으로 넘기는 조작을 어디에 두는지. */
enum class SceneryControl { BUTTON, CHIP }

/** 넓은 창의 홈은 운전석 쪽 버튼 줄에 둔다. 버튼 줄이 좁은 1/3 창과 다른 화면은 지도 위 단추를 쓴다. */
fun sceneryControl(window: WindowClass, home: Boolean): SceneryControl =
    if (home && window != WindowClass.THIRD) SceneryControl.BUTTON else SceneryControl.CHIP

/** 코스를 고를 때 보는 칸. 값을 낼 수 없는 칸은 빠진다. [lightMinutes]는 쉬지 않고 달리는 시간이다. */
internal fun choiceFacts(
    course: Course,
    facts: CourseFacts,
    lightMinutes: Int,
    dayOfYear: Int,
    minuteOfDay: Int,
    utcOffsetMinutes: Int,
    energy: EnergyRate?,
): List<Fact> {
    val start = course.path.first()
    val sun = sunTimes(dayOfYear, start.lat, start.lon, utcOffsetMinutes)
    return listOfNotNull(
        Fact(kmText(facts.meters), "거리"),
        seasonFit(course.tags.seasons, dayOfYear)?.let { Fact("$it%", "계절 적합") },
        timeFit(course.tags.times, minuteOfDay, lightMinutes, sun)?.let { Fact("$it%", "시간 적합") },
        Fact("${funScore(facts, course.elevation.isNotEmpty())}점", "운전 즐거움"),
        energy?.let { Fact(energyText(facts.meters, it), "연료량") },
    )
}

fun energyText(meters: Double, rate: EnergyRate): String =
    String.format(Locale.ROOT, "%.1f%s", energyNeeded(meters, rate), if (rate.electric) "kWh" else "L")

/** 구간 분위기의 이름. 물가는 코스 주제로 바다, 호수, 강을 가른다. */
private fun sceneryWord(mood: String, themes: List<String>): String = when (mood) {
    "forest" -> "숲"
    "city" -> "도심"
    else -> when {
        "coast" in themes -> "바다"
        "lake" in themes -> "호수"
        "river" in themes -> "강"
        else -> "물가"
    }
}

/** 경치 한 줄. 비율이 큰 풍경부터 쓰고 전망 명소 수를 붙인다. */
fun sceneryLine(moodShare: Map<String, Int>, themes: List<String>, views: Int): String =
    (moodShare.filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { "${sceneryWord(it.key, themes)} ${it.value}%" } +
        listOfNotNull("전망 명소 ${views}곳".takeIf { views > 0 })).joinToString(" · ")

fun paceText(minutes: Int): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0 -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

/** 예상 소요 시간 한 칸. */
data class PaceCell(val time: String, val name: String, val note: String)

fun paceCells(pace: Pace): List<PaceCell> = listOf(
    PaceCell(paceText(pace.light), "가볍게", "쉬지 않고"),
    PaceCell(paceText(pace.rest), "쉬어 가며", "경치 좋은 곳 두 번"),
    PaceCell(paceText(pace.stay), "머물며", "명소와 식사까지"),
)

/** 경치 줄과 예상치 안내. 1/3 칸은 버튼 줄까지 화면에 들도록 한 줄로 합친다(화면 다듬기 설계 2절). */
fun sceneryNotes(scenery: String, third: Boolean): List<String> =
    if (third) listOf("$scenery · 예상치, 교통 제외") else listOf(scenery, "적합도·즐거움·소요 시간은 예상 · 교통 상황 제외")
