package com.byway.ui

import android.content.Context
import android.widget.Button
import com.byway.core.course.Course
import com.byway.service.MyDrives

/** 정차 화면들이 함께 받는 것. 지금 창 폭과 코스 수치, 코스를 고를 때 할 일을 담는다. */
internal class BrowseKit(val book: CourseBook, val widthDp: Int, val onCourse: (Course) -> Unit) {
    val wide = isWide(widthDp)
    val window = windowClassOf(widthDp)
    val edge = if (wide) Dimens.SPACE_10 else Dimens.SPACE_6
}

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
