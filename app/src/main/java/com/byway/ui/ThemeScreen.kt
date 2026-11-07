package com.byway.ui

import android.content.Context
import android.view.View
import com.byway.core.course.withTheme

/** 테마를 고르면 그 테마 코스 격자가 나온다. */
internal fun themeScreen(context: Context, kit: BrowseKit, theme: String?, onTheme: (String) -> Unit): View =
    shelfScreen(
        context,
        kit,
        choices = kit.book.themes.map { Choice(it.key, "${themeLabel(it.key)} ${it.count}") },
        selected = theme,
        empty = "테마가 정해진 코스가 아직 없습니다",
        coursesOf = { withTheme(kit.book.courses, it) },
        onPick = onTheme,
    )
