package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.byway.core.course.CourseTags

/** 코스를 고를 이유를 보이는 표시. 터치 대상이 아니다. */
internal fun Context.pickRow(tags: CourseTags, limit: Int = 3): View = row().apply {
    pickLabels(tags).take(limit).forEach { text ->
        val pick = label(text, Dimens.LABEL, palette.ink).atMostLines(1).apply {
            gravity = Gravity.CENTER
            background = rounded(palette.accentSoft, Dimens.RADIUS_SM)
            setPadding(dp(Dimens.SPACE_4), 0, dp(Dimens.SPACE_4), 0)
        }
        addView(pick, LinearLayout.LayoutParams(WRAP, dp(Dimens.PICK_HEIGHT)).apply { marginEnd = dp(Dimens.SPACE_2) })
    }
}

/** 코스 카드 아래 한 줄. 거리와 대표 추천 하나를 쓴다. */
fun coverNote(km: String, tags: CourseTags): String =
    listOfNotNull(km, pickLabels(tags).firstOrNull()).joinToString(" · ")
