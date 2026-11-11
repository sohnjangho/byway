package com.byway.ui

import com.byway.core.course.ScenePlace
import com.byway.core.course.Section
import com.byway.core.drive.Prompt
import java.util.Locale

/** 주행 화면이 그리는 값. [filled]는 구간마다 지나온 비율이다. */
data class DriveReadout(val sectionIndex: Int, val remainingKm: String, val filled: List<Float>)

/** [remainingMeters]가 null 이면 첫 위치가 오기 전이므로 코스 전체가 남았다. */
fun readoutOf(sectionMeters: List<Double>, sectionIndex: Int, remainingMeters: Double?): DriveReadout {
    val total = sectionMeters.sum()
    val remaining = remainingMeters ?: total
    val km = String.format(Locale.ROOT, "%.1f", remaining / 1_000)
    return DriveReadout(sectionIndex, km, filledShares(sectionMeters, total - remaining))
}

private fun filledShares(sectionMeters: List<Double>, travelled: Double): List<Float> {
    var left = travelled
    return sectionMeters.map { meters ->
        val share = (left / meters).coerceIn(0.0, 1.0)
        left -= meters
        share.toFloat()
    }
}

fun sectionCount(sectionIndex: Int, total: Int): String = "구간 ${sectionIndex + 1}/$total"

/** 엔진의 물음에 답하는 버튼의 글. 물음이 없으면 버튼도 없다. 누르면 그렇다는 답이다. */
fun promptButton(prompt: Prompt?): String? = when (prompt) {
    Prompt.DETOUR -> "잠깐 들러요"
    Prompt.RESUME -> "코스 이어가기"
    null -> null
}

/** 주행 화면의 한 줄. 달리는 중에 위치를 놓치면 안내 문구 대신 그 사실을 적는다. */
fun driveLine(line: String, stale: Boolean): String = if (stale) "위치 확인 중" else line

/** 달리다 만 코스를 이어갈지 묻는 글. */
fun resumeLine(courseName: String, sectionNo: Int): String = "$courseName 코스 ${sectionNo}구간을 이어갈까요?"

/** 코스 위의 지점이 몇 번째 구간인지. 1부터 센다. 코스 밖의 지점은 마지막 구간으로 본다. */
fun sectionNoAt(sections: List<Section>, pathIndex: Int): Int =
    sections.indexOfFirst { pathIndex in it.startIndex..it.endIndex }.let { if (it < 0) sections.size else it + 1 }

/** 앞으로 만날 곳. 지나온 곳은 빼고 가까운 차례로 [limit]곳까지다. */
fun placesAhead(places: List<ScenePlace>, drivenKm: Double, limit: Int): List<ScenePlace> =
    places.filter { it.km >= drivenKm }.sortedBy { it.km }.take(limit)

fun aheadKmText(place: ScenePlace, drivenKm: Double): String =
    String.format(Locale.ROOT, "%.1fkm", (place.km - drivenKm).coerceAtLeast(0.0))

/** 주행 화면 장소 줄. 홈의 코스 주변 줄처럼 종류·설명·거리를 한 줄에 쓰고, 거리는 지금 자리에서 남은 거리다. */
fun aheadLine(place: ScenePlace, drivenKm: Double): String =
    listOf(place.label, place.note, "${aheadKmText(place, drivenKm)} 앞").filter { it.isNotBlank() }.joinToString(" · ")
