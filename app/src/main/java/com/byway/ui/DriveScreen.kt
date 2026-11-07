package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import com.byway.core.course.Course
import com.byway.service.DriveState

private const val BAR_HEIGHT = 10

/**
 * 내비 옆 칸의 코스 주행 화면. 구간 이름, 남은 거리, 구간 막대, 한 줄, 코스 그림, 버튼 하나만 둔다.
 * 스크롤과 애니메이션은 없고 움직이는 것은 현재 위치 점뿐이다. 구간이 바뀌면 배경색이 그 자리에서 바뀐다.
 */
@SuppressLint("ViewConstructor")
internal class DriveScreen(
    context: Context,
    book: CourseBook,
    private val course: Course,
    wide: Boolean,
    onEnd: () -> Unit,
) : FrameLayout(context) {
    private val sectionMeters = book.sectionMeters(course)
    private val where = context.label("", Look.DRIVE_LINE, Look.MUTED).fixedLines(1)
    private val section = context.label("", Look.DRIVE_SECTION, bold = true).fixedLines(1)
    private val remaining = context.label("", Look.DRIVE_NUMBER, bold = true).tabular().apply { letterSpacing = -0.03f }
    private val bar = SectionBar(
        context,
        sectionMeters.map { (it / sectionMeters.sum()).toFloat() },
        course.sections.map { moodColor(it.mood) },
    )
    private val spoken = context.label("", Look.DRIVE_LINE).fixedLines(2)
    private val map = CoursePathView(context).apply {
        background = context.rounded(Look.SURFACE)
        show(course, book.stops(course))
    }

    init {
        val text = context.column().apply {
            addView(where)
            addSpaced(section, 12)
            val distance = LinearLayout(context).apply {
                addView(remaining)
                addView(
                    context.label("km", Look.DRIVE_UNIT),
                    LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(12) },
                )
            }
            addView(distance)
            addView(bar, LinearLayout.LayoutParams(MATCH, context.dp(BAR_HEIGHT)).apply { topMargin = context.dp(12) })
            addSpaced(spoken, 28)
        }
        val end = context.outlineButton("코스 끝내기", onEnd)
        val page = if (wide) {
            LinearLayout(context).apply {
                val left = context.column().apply {
                    addView(text)
                    addView(Space(context), fill())
                    addView(end)
                }
                addView(left, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginEnd = context.dp(48) })
                addView(map, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
        } else {
            context.column().apply {
                addView(text)
                addView(map, fill().apply { topMargin = context.dp(20) })
                addSpaced(end, 20)
            }
        }
        page.setPadding(context.dp(48), context.dp(48), context.dp(48), context.dp(40))
        addView(page, matchFrame())
    }

    fun show(state: DriveState) {
        val readout = readoutOf(course, sectionMeters, state.line)
        val current = course.sections[readout.sectionIndex]
        setBackgroundColor(airColor(current.mood))
        where.text = "${course.name} · 구간 ${readout.sectionIndex + 1} / ${course.sections.size}"
        section.text = current.name
        section.setTextColor(moodColor(current.mood))
        remaining.text = readout.remainingKm
        bar.show(readout.filled)
        spoken.text = state.spoken
        map.moveTo(state.position)
    }
}
