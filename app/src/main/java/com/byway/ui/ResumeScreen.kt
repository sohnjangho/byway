package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import com.byway.core.course.Course

/**
 * 달리다 만 코스를 이어갈지 묻는다. 시동을 끄거나 앱이 꺼져도 코스가 거기서 끝나지 않게 한다.
 * 정차 화면이고, 어느 쪽이든 골라야 메뉴로 넘어간다.
 */
internal fun resumeScreen(
    context: Context,
    kit: BrowseKit,
    course: Course,
    progress: Int,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
): View = with(context) {
    val sectionNo = sectionNoAt(course.sections, progress)
    val text = column().apply {
        addView(label("달리던 코스가 있습니다", Dimens.SMALL, palette.inkSecondary))
        addSpaced(label(resumeLine(course.name, sectionNo), Dimens.HEADING, bold = true), Dimens.SPACE_3)
        addSpaced(label("${course.sections[sectionNo - 1].name} 구간부터 내비에 다시 올립니다", Dimens.BODY, palette.inkSecondary), Dimens.SPACE_3)
        addView(Space(context), fill())
        val buttons = LinearLayout(context).apply {
            addView(primaryButton("이어가기", onResume), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_4) })
            addView(quietButton("그만두기", onDiscard), LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        addSpaced(buttons, Dimens.SPACE_6)
    }
    val drawing = CoursePathView(this).apply {
        asCard()
        show(course, emptyList())
    }
    val edge = dp(kit.edge)
    panes(text, drawing, kit.wide).apply {
        setBackgroundColor(palette.bg)
        setPadding(edge, edge, edge, edge)
    }
}
