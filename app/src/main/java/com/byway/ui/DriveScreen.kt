package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import com.byway.core.course.Course
import com.byway.core.course.CourseScene
import com.byway.service.DriveState
import com.byway.service.SceneCatalog

/**
 * 내비 옆 칸의 코스 주행 화면. 코스 이름과 구간, 남은 거리, 구간 막대, 한 줄 안내, 지도와 위치 점, 코스 끝내기만 둔다.
 * 스크롤과 애니메이션은 없고 움직이는 것은 현재 위치 점뿐이다. 구간이 바뀌면 배경색이 그 자리에서 바뀐다.
 */
@SuppressLint("ViewConstructor")
internal class DriveScreen(
    context: Context,
    book: CourseBook,
    private val course: Course,
    wide: Boolean,
    onAnswer: () -> Unit,
    onEnd: () -> Unit,
) : FrameLayout(context) {
    private val colors = context.palette
    private val sectionMeters = book.sectionMeters(course)
    private val scene: CourseScene? = SceneCatalog.of(context, course.id)?.takeIf { it.track.isNotEmpty() && Covers.hasScene(context, course.id, context.night) }
    private val where = context.label("", Dimens.LABEL, colors.inkSecondary).tabular().fixedLines(1)
    private val section = context.label("", Dimens.DRIVE_SECTION, bold = true).fixedLines(1)
    private val remaining = context.label("", Dimens.DRIVE_NUMBER, bold = true).tabular().apply { letterSpacing = Dimens.DRIVE_NUMBER_SPACING }
    private val bar = context.sectionBar(book, course)
    private val noteMark = View(context)
    private val spoken = context.label("", Dimens.DRIVE_LINE).fixedLines(2).apply { gravity = Gravity.CENTER_VERTICAL }
    private val answer = context.outlineButton("", onAnswer).apply { visibility = GONE }
    private val mapView = scene?.let { DriveMapView(context, course.id, it) }
    private val pathView = if (mapView == null) CoursePathView(context).apply {
        background = context.rounded(colors.bgRaised)
        show(course, book.stops(course))
    } else null

    init {
        val end = context.outlineButton("코스 끝내기", onEnd)
        val head = heading()
        val distance = distanceBlock()
        val note = noteBlock()
        val map = mapBlock()
        val page = if (wide) {
            LinearLayout(context).apply {
                // 글이 위에 몰리지 않게 묶음 사이를 고르게 벌린다.
                val left = context.column().apply {
                    addView(head)
                    addView(Space(context), fill())
                    addView(distance)
                    addView(Space(context), fill())
                    addView(note)
                    addView(Space(context), fill())
                    addView(answer)
                    addSpaced(end, Dimens.SPACE_3)
                }
                addView(left, LinearLayout.LayoutParams(0, MATCH, TEXT_WEIGHT).apply { marginEnd = context.dp(Dimens.DRIVE_EDGE) })
                addView(map, LinearLayout.LayoutParams(0, MATCH, MAP_WEIGHT))
            }
        } else {
            context.column().apply {
                addView(head)
                addSpaced(distance, Dimens.SPACE_3)
                addSpaced(note, Dimens.SPACE_4)
                addView(map, fill().apply { topMargin = context.dp(Dimens.SPACE_4) })
                addSpaced(answer, Dimens.SPACE_4)
                addSpaced(end, Dimens.SPACE_3)
            }
        }
        val edge = context.dp(Dimens.DRIVE_EDGE)
        page.setPadding(edge, edge, edge, context.dp(Dimens.SPACE_10))
        addView(page, matchFrame())
    }

    private fun heading(): View = context.column().apply {
        addView(context.row().apply {
            addView(context.label(course.name, Dimens.DRIVE_LINE, bold = true).atMostLines(1), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(where)
        })
        addSpaced(section, Dimens.SPACE_2)
    }

    private fun distanceBlock(): View = context.column().apply {
        addView(LinearLayout(context).apply {
            addView(remaining)
            addView(context.label("km", Dimens.DRIVE_UNIT, colors.inkSecondary), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(Dimens.SPACE_3) })
            addView(context.label("남은 거리", Dimens.DRIVE_LINE, colors.inkSecondary).atMostLines(1), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(Dimens.SPACE_3) })
        })
        addView(bar, LinearLayout.LayoutParams(MATCH, context.dp(Dimens.DRIVE_BAR_HEIGHT)).apply { topMargin = context.dp(Dimens.SPACE_3) })
    }

    /** 안내 문구. 왼쪽 선은 지금 구간의 색이다. */
    private fun noteBlock(): View = context.row().apply {
        background = context.rounded(colors.bgRaised, Dimens.RADIUS_SM)
        clipToOutline = true
        minimumHeight = context.dp(Dimens.DRIVE_NOTE_HEIGHT)
        addView(noteMark, LinearLayout.LayoutParams(context.dp(Dimens.DRIVE_NOTE_MARK), MATCH))
        val inset = context.dp(Dimens.SPACE_6)
        addView(spoken, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(inset, context.dp(Dimens.SPACE_3), inset, context.dp(Dimens.SPACE_3)) })
    }

    private fun mapBlock(): View = FrameLayout(context).apply {
        addView(mapView ?: requireNotNull(pathView), matchFrame())
        if (mapView != null) {
            val credit = context.label(driveMapCredit(currentMapStyle()), Dimens.LABEL, colors.onPhoto).apply {
                background = context.rounded(colors.photoScrim, Dimens.RADIUS_SM)
                val inset = context.dp(Dimens.CREDIT_PADDING)
                setPadding(context.dp(Dimens.SPACE_3), inset, context.dp(Dimens.SPACE_3), inset)
            }
            val edge = context.dp(Dimens.SPACE_3)
            addView(credit, LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply { setMargins(edge, edge, edge, edge) })
        }
    }

    fun show(state: DriveState) {
        val readout = readoutOf(sectionMeters, state.sectionIndex, state.remainingMeters)
        val current = course.sections[readout.sectionIndex]
        setBackgroundColor(colors.airColor(current.mood))
        where.text = sectionCount(readout.sectionIndex, course.sections.size)
        section.text = current.name
        section.setTextColor(colors.moodColor(current.mood))
        remaining.text = readout.remainingKm
        bar.show(readout.filled)
        noteMark.setBackgroundColor(colors.moodColor(current.mood))
        spoken.text = driveLine(state.line, state.stale)
        val title = promptButton(state.prompt)
        answer.text = title.orEmpty()
        answer.visibility = if (title == null) GONE else VISIBLE
        scene?.let { mapView?.moveTo(trackAt(it.track, drivenKm(it.totalKm, state.remainingMeters))) }
        pathView?.moveTo(state.position)
    }

    private companion object {
        const val TEXT_WEIGHT = 2f
        const val MAP_WEIGHT = 3f
    }
}
