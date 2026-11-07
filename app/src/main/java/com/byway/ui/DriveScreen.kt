package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import android.widget.Space
import com.byway.core.course.Course
import com.byway.service.DriveState

/** 코스 주행 화면. 한 줄, 음성 문구, 차 안 값, 코스 그림, 버튼 1개만 둔다. 스크롤과 애니메이션은 없다. */
@SuppressLint("ViewConstructor")
class DriveScreen(context: Context, course: Course?, onEnd: () -> Unit) : FrameLayout(context) {
    private val line = context.label("", 64f, bold = true).fixedLines(2)
    private val spoken = context.label("", 32f, Look.ACCENT).fixedLines(2)
    private val cabin = context.label("", Look.BODY, Look.MUTED).fixedLines(1)
    private val map = context.courseMap().apply { course?.let { show(it.path, naviStops(it)) } }

    init {
        val left = context.column().apply {
            addView(line)
            addSpaced(spoken, 32)
            addSpaced(cabin, 16)
            addView(Space(context), fill())
            addView(context.primaryButton("코스 끝내기", onEnd))
        }
        addView(context.twoColumns(left, map, leftWeight = 1.2f))
    }

    fun show(state: DriveState) {
        line.text = state.line
        spoken.text = if (state.spoken.isEmpty()) "" else "음성: ${state.spoken}"
        cabin.text = if (state.cabin.isEmpty()) "" else "차 안: ${state.cabin}"
        map.moveTo(state.position)
    }
}
