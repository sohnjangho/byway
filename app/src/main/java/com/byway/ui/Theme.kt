package com.byway.ui

import android.content.Context
import android.content.res.Configuration

/** 앱은 시스템 주야 모드를 따른다. 앱 안에 테마 선택을 두지 않는다. */
fun isNight(uiMode: Int): Boolean = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun paletteFor(uiMode: Int): Palette = if (isNight(uiMode)) NightPalette else DayPalette

internal val Context.night: Boolean get() = isNight(resources.configuration.uiMode)

/** 지금 테마의 색. 화면은 색을 이것으로만 읽는다. */
internal val Context.palette: Palette get() = paletteFor(resources.configuration.uiMode)

/** 구간 분위기별 색. 코스 선, 구간 점, 구간 막대, 구간 이름이 같은 색을 쓴다. */
fun Palette.moodColor(mood: String?): Int = when (mood) {
    "forest" -> sectionForest
    "river" -> sectionRiver
    else -> sectionCity
}

/** 구간 분위기별 주행 칸 배경. */
fun Palette.airColor(mood: String?): Int = when (mood) {
    "forest" -> airForest
    "river" -> airRiver
    else -> airCity
}
