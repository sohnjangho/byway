package com.byway.ui

import android.content.Context
import android.view.View
import com.byway.core.course.inRegion

/** 지역을 고르면 그 지역 코스 격자가 나온다. */
internal fun regionScreen(context: Context, kit: BrowseKit, region: String?, onRegion: (String) -> Unit): View =
    shelfScreen(
        context,
        kit,
        choices = kit.book.regions.map { Choice(it.key, "${it.key} ${it.count}") },
        selected = region,
        empty = "지역이 정해진 코스가 아직 없습니다",
        coursesOf = { inRegion(kit.book.courses, it) },
        onPick = onRegion,
    )
