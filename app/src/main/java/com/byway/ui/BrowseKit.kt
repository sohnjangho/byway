package com.byway.ui

import android.content.Context
import android.widget.Button
import com.byway.core.course.Course
import com.byway.service.MyDrives

/** 정차 화면들이 함께 받는 것. 지금 창 폭과 코스 수치, 코스를 고를 때 할 일을 담는다. */
internal class BrowseKit(val book: CourseBook, val widthDp: Int, val onCourse: (Course) -> Unit) {
    val wide = isWide(widthDp)
    val edge = if (wide) Look.EDGE else Look.NARROW_EDGE

    /** 한 줄에 [count]장이 들어가는 카드 폭(dp). [used]는 옆에 다른 칸이 차지한 폭이다. */
    fun cellWidth(count: Int, used: Int = 0): Int =
        (widthDp - used - 2 * edge - Look.CARD_GAP * (count - 1)) / count
}

/** 코스 커버 카드들을 격자로 놓는다. */
internal fun Context.courseGrid(kit: BrowseKit, courses: List<Course>) = grid(
    courses.map { course ->
        coverCard(course, kit.book.character(course), dp(GRID_CELL)) { kit.onCourse(course) }
    },
    columns = if (kit.wide) 4 else 2,
    rowHeight = dp(GRID_CELL),
)

private const val GRID_CELL = 320

/** 코스를 저장하거나 저장을 푸는 버튼. 글자만 바뀐다. */
internal fun Context.saveToggle(course: Course, make: Context.(String, () -> Unit) -> Button): Button {
    fun title() = if (course.id in MyDrives.saved(this)) "저장됨" else "이 코스 저장"
    lateinit var button: Button
    button = make(title()) {
        MyDrives.toggleSaved(this, course.id)
        button.text = title()
    }
    return button
}
