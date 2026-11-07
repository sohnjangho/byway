package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import com.byway.core.course.Course
import com.byway.service.DriveState

/**
 * 내비 옆 칸의 코스 주행 화면. 코스 이름과 구간 번호, 구간 이름, 남은 거리, 구간 막대, 한 줄 안내,
 * 코스 그림과 위치 점, 코스 끝내기만 둔다. 스크롤과 애니메이션은 없고 움직이는 것은 현재 위치 점뿐이다.
 * 구간이 바뀌면 배경색이 그 자리에서 바뀐다.
 */
@SuppressLint("ViewConstructor")
internal class DriveScreen(
    context: Context,
    book: CourseBook,
    private val course: Course,
    wide: Boolean,
    onEnd: () -> Unit,
) : FrameLayout(context) {
    private val colors = context.palette
    private val sectionMeters = book.sectionMeters(course)
    private val where = context.label("", Dimens.DRIVE_LINE, colors.inkSecondary).fixedLines(1)
    private val section = context.label("", Dimens.DRIVE_SECTION, bold = true).fixedLines(1)
    private val remaining = context.label("", Dimens.DRIVE_NUMBER, bold = true).tabular().apply { letterSpacing = Dimens.DRIVE_NUMBER_SPACING }
    private val bar = context.sectionBar(book, course)
    private val spoken = context.label("", Dimens.DRIVE_LINE).fixedLines(2)
    private val map = CoursePathView(context).apply {
        background = context.rounded(colors.bgRaised)
        show(course, book.stops(course))
    }

    init {
        val text = context.column().apply {
            addView(where)
            addSpaced(section, Dimens.SPACE_3)
            val distance = LinearLayout(context).apply {
                addView(remaining)
                addView(
                    context.label("km", Dimens.DRIVE_UNIT),
                    LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(Dimens.SPACE_3) },
                )
            }
            addView(distance)
            addView(
                bar,
                LinearLayout.LayoutParams(MATCH, context.dp(Dimens.DRIVE_BAR_HEIGHT)).apply { topMargin = context.dp(Dimens.SPACE_3) },
            )
            addSpaced(spoken, Dimens.SPACE_6)
        }
        val end = context.outlineButton("코스 끝내기", onEnd)
        val page = if (wide) {
            LinearLayout(context).apply {
                val left = context.column().apply {
                    addView(text)
                    addView(Space(context), fill())
                    addView(end)
                }
                addView(left, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginEnd = context.dp(Dimens.DRIVE_EDGE) })
                addView(map, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
        } else {
            context.column().apply {
                addView(text)
                addView(map, fill().apply { topMargin = context.dp(Dimens.SPACE_6) })
                addSpaced(end, Dimens.SPACE_6)
            }
        }
        val edge = context.dp(Dimens.DRIVE_EDGE)
        page.setPadding(edge, edge, edge, context.dp(Dimens.SPACE_10))
        addView(page, matchFrame())
    }

    fun show(state: DriveState) {
        val readout = readoutOf(sectionMeters, state.sectionIndex, state.remainingMeters)
        val current = course.sections[readout.sectionIndex]
        setBackgroundColor(colors.airColor(current.mood))
        where.text = "${course.name} · 구간 ${readout.sectionIndex + 1} / ${course.sections.size}"
        section.text = current.name
        section.setTextColor(colors.moodColor(current.mood))
        remaining.text = readout.remainingKm
        bar.show(readout.filled)
        spoken.text = state.line
        map.moveTo(state.position)
    }
}
