package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.byway.core.course.Course

/** 코스 분위기 배경의 색. 구간 범위 밖이면 첫 구간이고, 구간이 없으면 기본 분위기다. */
fun moodTint(course: Course, sectionIndex: Int, palette: Palette): Int =
    palette.airColor((course.sections.getOrNull(sectionIndex) ?: course.sections.firstOrNull())?.mood)

/** 분위기 배경에 코스 사진을 흐리게 깔 때 화면에 적을 사진 출처. 사진이 없으면 null 이다. */
fun moodPhotoCredit(course: Course, hasPhoto: Boolean): String? =
    course.sceneryCredit.takeIf { hasPhoto && it.isNotBlank() }?.let { photoCreditLine(it, changed = true) }

/**
 * 코스가 정해진 화면의 바탕(화면 다듬기 설계 4절). 구간 분위기 색 위에 흐린 코스 사진을 화면 전체에 깔고,
 * 사진을 쓰면 그 출처를 오른쪽 위에 한 줄로 적는다. 코스가 없으면 바탕색이다. 움직이지 않는다.
 * 내용은 [hold]로 배경과 출처 사이에 넣는다. 메뉴가 위에 떠 있는 탭(1/3 칸)은 [creditTop](dp)만큼 출처를 내린다.
 */
@SuppressLint("ViewConstructor")
internal class MoodLayer(context: Context, window: WindowClass, creditTop: Int = 0) : FrameLayout(context) {
    private val backdrop = DriveBackdrop(context, window, full = true)
    private val credit = context.label("", Dimens.CREDIT, context.palette.onPhoto).apply {
        background = context.rounded(context.palette.photoScrim, Dimens.RADIUS_SM)
        val inset = context.dp(Dimens.CREDIT_PADDING)
        setPadding(context.dp(Dimens.SPACE_2), inset, context.dp(Dimens.SPACE_2), inset)
        visibility = GONE
    }
    private var shown: String? = null

    init {
        setBackgroundColor(context.palette.bg)
        addView(backdrop, matchFrame())
        addView(credit, LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
            topMargin = context.dp(Dimens.SPACE_2 + creditTop)
            marginEnd = context.dp(Dimens.SPACE_3)
        })
    }

    fun hold(content: View): MoodLayer = apply { addView(content, 1, matchFrame()) }

    /** 코스의 분위기를 깐다. 같은 코스면 다시 읽지 않는다. */
    fun show(course: Course?) {
        if (course?.id == shown) return
        shown = course?.id
        backdrop.clearPicture()
        if (course == null) {
            backdrop.visibility = GONE
            credit.visibility = GONE
            return
        }
        backdrop.visibility = VISIBLE
        backdrop.setTint(moodTint(course, 0, context.palette))
        val hasPhoto = canSwipeToScenery(course.sceneryCredit, Covers.hasScenery(context, course))
        if (hasPhoto) Covers.loadScenery(context, course) { if (shown == course.id) backdrop.setPicture(it) }
        val line = moodPhotoCredit(course, hasPhoto)
        credit.text = line.orEmpty()
        credit.visibility = if (line == null) GONE else VISIBLE
    }

    /** 주행 중 구간이 바뀌면 그 구간 분위기 색으로 바꾼다. */
    fun setTint(color: Int) = backdrop.setTint(color)
}
