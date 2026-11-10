package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.byway.core.course.Course
import com.byway.core.course.CourseScene
import com.byway.core.geo.GeoFrame
import com.byway.core.geo.GeoPoint
import com.byway.core.geo.partyFrame
import com.byway.service.DriveState
import com.byway.service.SceneCatalog

/**
 * 내비 옆 칸의 코스 주행 화면. 홈과 같이 지도 장면을 깔고 글 칸을 경계 없이 잇는다.
 * 글 칸에는 코스와 구간, 남은 거리, 안내 문구, 앞으로 만날 곳을 두고, 그 뒤에 코스 사진과 구간 색을 깐다. 지도에는 경로와 번호, 위치 점이 있다.
 * 스크롤과 애니메이션, 분위기 효과는 없고 움직이는 것은 위치 점뿐이다.
 * 파티로 달릴 때 내 차 5km 안에 멤버가 있으면 지도 자리에 확대 지도를 덮는다(주행 화면 3분할 설계 3절). 범위는 점이 가장자리로 나갈 때만 한 번에 바뀐다.
 */
@SuppressLint("ViewConstructor")
internal class DriveScreen(
    context: Context,
    book: CourseBook,
    private val course: Course,
    private val window: WindowClass,
    onAnswer: () -> Unit,
    onEnd: () -> Unit,
) : FrameLayout(context) {
    private val colors = context.palette
    private val sectionMeters = book.sectionMeters(course)
    private val scene: CourseScene? = SceneCatalog.of(context, course.id)
    private val places = shownPlaces(scene?.places.orEmpty())
    private val third = window == WindowClass.THIRD
    private val full = window == WindowClass.FULL

    // 넓은 창에서 list 는 아래에 사진 띠가 없으니 출처를 아래로 내린다는 뜻이다.
    // 1/3 창은 지도가 주인공이라 칸을 키우고(752dp) 줄이지 않은 장면에 경로를 다 담는다.
    private val sceneView = SceneView(context, window, list = !third, hintOnMap = false)
    private val backdrop = DriveBackdrop(context, window)
    private val where = context.label("", Dimens.LABEL, colors.inkSecondary).tabular().fixedLines(1)
    private val section = context.label("", if (third) Dimens.THIRD_DRIVE_SECTION else Dimens.DRIVE_SECTION, bold = true).fixedLines(1)
    private val remaining = context.label("", if (third) Dimens.THIRD_DRIVE_NUMBER else Dimens.DRIVE_NUMBER, bold = true).tabular().apply { letterSpacing = Dimens.DRIVE_NUMBER_SPACING }
    private val bar = context.sectionBar(book, course)
    // 1/3 칸은 지도에 자리를 내느라 안내 문구를 한 줄로 둔다. 차 안내는 가운데 내비가 맡는다.
    private val spoken = context.label("", if (third) Dimens.BODY else Dimens.DRIVE_LINE).fixedLines(if (third) 1 else 2)
    private val aheadTitle = context.label("코스 주변", Dimens.SMALL, bold = true)
    private val rows = List(if (third) AHEAD_THIRD else AHEAD_WIDE) { AheadRow(context) }
    private val answer = context.primaryButton("", onAnswer).apply { visibility = GONE }
    private val end = context.outlineButton("코스 끝내기", onEnd)

    /** 파티 확대 지도. 크기를 재야 범위를 맞출 수 있어 처음부터 자리를 잡아 두고 INVISIBLE 로 숨긴다. */
    private val zoomView = CoursePathView(context).apply { visibility = INVISIBLE }
    private var zoomFrame: GeoFrame? = null

    init {
        setBackgroundColor(colors.bg)
        val hasPicture = scene != null && Covers.hasScene(context, course.id, context.night)
        val sideWidth = context.dp(Dimens.RAIL_WIDTH + if (full) Dimens.SIDE_WIDTH_FULL else Dimens.SIDE_WIDTH)
        val hasPhoto = canSwipeToScenery(course.sceneryCredit, Covers.hasScenery(context, course))
        if (hasPhoto) Covers.loadScenery(context, course, backdrop::setPicture)
        addView(backdrop, matchFrame())
        sceneView.show(course, scene, places, context.night, sceneryAvailable = false, reveal = false)
        val style = currentMapStyle()
        // 지도 출처 한 줄, 글 칸 배경에 코스 사진을 흐리게 깔면 그 사진 출처 한 줄이다.
        sceneView.setCredit(listOfNotNull(mapCreditLine(style), course.sceneryCredit.takeIf { hasPhoto }?.let { photoCreditLine(it, changed = true) }).joinToString("\n"))
        // 코스 그림으로 대신할 때는 글 칸과 겹치지 않게 옆에 둔다.
        addView(sceneView, LayoutParams(MATCH, if (third) context.dp(Dimens.THIRD_DRIVE_SCENE_HEIGHT) else MATCH).apply {
            if (!third && !hasPicture) leftMargin = sideWidth
        })
        zoomView.show(course, emptyList())
        addView(zoomView, LayoutParams(MATCH, if (third) context.dp(Dimens.THIRD_DRIVE_SCENE_HEIGHT) else MATCH).apply {
            if (!third) leftMargin = sideWidth
        })

        val info = infoColumn()
        val near = nearColumn()
        val side = if (full) {
            LinearLayout(context).apply {
                addView(info, LinearLayout.LayoutParams(context.dp(Dimens.SIDE_INFO_WIDTH), MATCH).apply { marginEnd = context.dp(Dimens.SPACE_10) })
                addView(near, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
        } else {
            context.column().apply {
                addView(info)
                addView(near, fill().apply { topMargin = context.dp(if (third) Dimens.SPACE_3 else Dimens.SPACE_6) })
            }
        }
        val edge = context.dp(if (third) Dimens.SPACE_6 else Dimens.SPACE_8)
        side.setPadding(edge, if (third) context.dp(Dimens.SPACE_2) else edge, edge, context.dp(if (third) Dimens.SPACE_4 else Dimens.SPACE_6))
        addView(side, if (third) {
            LayoutParams(MATCH, MATCH).apply { topMargin = context.dp(Dimens.THIRD_DRIVE_SCENE_HEIGHT) }
        } else {
            LayoutParams(sideWidth, MATCH)
        })
    }

    private fun infoColumn(): View = context.column().apply {
        addView(context.label(course.name, if (third) Dimens.THIRD_DRIVE_NAME else Dimens.SIDE_NAME, bold = true).atMostLines(1))
        if (!third) addSpaced(context.pickRow(course.tags), Dimens.SPACE_2)
        addSpaced(context.row().apply {
            addView(section, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(where)
        }, if (third) Dimens.SPACE_2 else Dimens.SPACE_6)
        addView(distanceRow())
        addView(bar, LinearLayout.LayoutParams(MATCH, context.dp(Dimens.DRIVE_BAR_HEIGHT)).apply { topMargin = context.dp(if (third) Dimens.SPACE_2 else Dimens.SPACE_3) })
        addSpaced(spoken, if (third) Dimens.SPACE_2 else Dimens.SPACE_4)
    }

    private fun distanceRow(): View = LinearLayout(context).apply {
        addView(remaining)
        addView(context.label("km", Dimens.DRIVE_UNIT, colors.inkSecondary), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(Dimens.SPACE_3) })
        addView(context.label("남은 거리", Dimens.DRIVE_LINE, colors.inkSecondary).atMostLines(1), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(Dimens.SPACE_3) })
    }

    /** 앞으로 만날 곳과 버튼. 버튼은 홈처럼 아래에 붙고 코스 끝내기의 오른쪽 끝은 물음이 떠도 그대로다. */
    private fun nearColumn(): View = context.column().apply {
        if (!third) addView(aheadTitle)
        rows.forEachIndexed { i, row -> addSpaced(row.view, if (i == 0 && third) 0 else Dimens.SPACE_3) }
        addView(Space(context), fill())
        addView(LinearLayout(context).apply {
            addView(answer, LinearLayout.LayoutParams(0, WRAP, ANSWER_WEIGHT).apply { marginEnd = context.dp(Dimens.SPACE_3) })
            addView(end, LinearLayout.LayoutParams(0, MATCH, 1f))
        })
    }

    fun show(state: DriveState) {
        val readout = readoutOf(sectionMeters, state.sectionIndex, state.remainingMeters)
        val current = course.sections[readout.sectionIndex]
        where.text = sectionCount(readout.sectionIndex, course.sections.size)
        section.text = current.name
        section.setTextColor(colors.moodColor(current.mood))
        backdrop.setTint(colors.airColor(current.mood))
        remaining.text = readout.remainingKm
        bar.show(readout.filled)
        spoken.text = driveLine(state.line, state.stale)
        val title = promptButton(state.prompt)
        answer.text = title.orEmpty()
        answer.visibility = if (title == null) GONE else VISIBLE
        val driven = scene?.let { drivenKm(it.totalKm, state.remainingMeters) } ?: 0.0
        showAhead(driven)
        scene?.let { sceneView.setMarker(trackAt(it.track, driven)) }
        sceneView.moveFallbackTo(state.position)
        val pins = partyPins(state)
        sceneView.setPins(pins)
        showZoom(state.position, pins)
    }

    /** 내 위치와 칸 크기를 알고 5km 안에 멤버가 있을 때만 확대 지도를 보인다. 아니면 코스 장면이 그대로 보인다. */
    private fun showZoom(me: GeoPoint?, pins: List<com.byway.ui.party.MapPin>) {
        val zoom = me?.takeIf { zoomView.width > 0 && zoomView.height > 0 }
            ?.let { partyFrame(it, pins.map { pin -> pin.point }, zoomView.width.toDouble() / zoomView.height, zoomFrame) }
        zoomFrame = zoom?.frame
        zoomView.visibility = if (zoom == null) INVISIBLE else VISIBLE
        if (zoom == null) return
        zoomView.zoom(zoom.frame, zoom.far)
        zoomView.moveTo(me)
        zoomView.setPins(pins)
    }

    /** 줄 수를 고정해 지나온 곳이 빠져도 아래 글이 움직이지 않는다. 가장 가까운 곳의 번호는 주황이다. */
    private fun showAhead(driven: Double) {
        val ahead = placesAhead(places, driven, rows.size)
        aheadTitle.visibility = if (ahead.isEmpty()) INVISIBLE else VISIBLE
        rows.forEachIndexed { i, row -> row.show(ahead.getOrNull(i), driven, nearest = i == 0) }
    }

    private companion object {
        const val AHEAD_WIDE = 6
        const val AHEAD_THIRD = 1
        const val ANSWER_WEIGHT = 2f
    }
}

/** 앞으로 만날 곳 한 줄. 번호와 이름, 설명, 남은 거리다. */
private class AheadRow(private val context: Context) {
    private val number = context.label("", Dimens.LABEL, bold = true).tabular().apply { gravity = Gravity.CENTER }
    private val name = context.label("", Dimens.SMALL, bold = true).atMostLines(1)
    private val line: TextView = context.label("", Dimens.LABEL, context.palette.ink).atMostLines(1)
    private val distance = context.label("", Dimens.SMALL, bold = true).tabular().fixedLines(1)

    val view: View = context.row().apply {
        addView(number, LinearLayout.LayoutParams(context.dp(Dimens.PLACE_NUMBER), context.dp(Dimens.PLACE_NUMBER)).apply { marginEnd = context.dp(Dimens.SPACE_3) })
        addView(context.column().apply {
            addView(name)
            addView(line)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(distance, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(Dimens.SPACE_3) })
    }

    fun show(place: com.byway.core.course.ScenePlace?, driven: Double, nearest: Boolean) {
        view.visibility = if (place == null) View.INVISIBLE else View.VISIBLE
        if (place == null) return
        number.text = "${place.no}"
        context.paintBadge(number, nearest)
        name.text = place.name
        line.text = aheadLine(place)
        distance.text = aheadKmText(place, driven)
    }
}

/** 함께 달리는 파티의 다른 멤버 핀(공유 설계 6-5). 주행 중에는 핀만 보이고 조작은 없다. */
internal fun partyPins(state: com.byway.service.DriveState): List<com.byway.ui.party.MapPin> {
    val party = state.party ?: return emptyList()
    return com.byway.service.livePins(party).mapNotNull { pin ->
        party.members.firstOrNull { it.id == pin.memberId }?.let { com.byway.ui.party.MapPin(pin.memberId, com.byway.core.geo.GeoPoint(pin.lat, pin.lon), it.avatar, it.nickname) }
    }
}
