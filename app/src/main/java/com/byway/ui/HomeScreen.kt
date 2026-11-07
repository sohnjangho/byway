package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked
import com.byway.core.course.Shelf
import com.byway.core.course.inRegion

private const val TOP_COUNT = 3

/** 정차 중 첫 화면. 음악 앱처럼 시간대 추천, 랭킹, 테마, 지역을 한눈에 둘러본다. */
internal fun homeScreen(
    context: Context,
    kit: BrowseKit,
    hour: Int,
    region: String?,
    onRegion: (String) -> Unit,
    onTheme: (String) -> Unit,
    onRanking: () -> Unit,
): View = with(context) {
    val hero = heroCard(kit, kit.book.suggested(hour), suggestionLine(hour))
    val top = topCurves(kit, onRanking)
    val edge = dp(kit.edge)
    val body = column().apply {
        setPadding(0, 0, 0, dp(Dimens.SPACE_8))
        // 카드 그림자가 나올 자리를 두려고 첫 줄은 폭 전체를 쓰고 여백을 안쪽에 둔다.
        val first = if (kit.wide) {
            LinearLayout(context).apply {
                addView(hero, LinearLayout.LayoutParams(0, dp(Dimens.HERO_HEIGHT), Dimens.HERO_WEIGHT).apply { marginEnd = dp(Dimens.SPACE_6) })
                addView(top, LinearLayout.LayoutParams(0, dp(Dimens.HERO_HEIGHT), Dimens.TOP_LIST_WEIGHT))
            }
        } else {
            column().apply {
                addView(hero)
                addSpaced(top, Dimens.SPACE_6)
            }
        }
        addView(first.apply { setPadding(edge, dp(Dimens.SPACE_6), edge, dp(Dimens.SPACE_6)) })
        val rest = column().apply {
            setPadding(edge, 0, edge, 0)
            addView(themeShelf(kit, onTheme))
            regionShelf(kit, region, onRegion)?.let { addSpaced(it, Dimens.SPACE_6) }
        }
        addView(rest)
    }
    ScrollView(context).apply { addView(body) }
}

private fun Context.heading(text: String) = label(text, Dimens.HEADING, bold = true)

private fun Context.heroCard(kit: BrowseKit, course: Course, line: String): View {
    val text = heroText(kit, course, line)
    val card = if (kit.wide) {
        LinearLayout(this).apply {
            addView(cover(course, dp(Dimens.HERO_HEIGHT)), LinearLayout.LayoutParams(dp(Dimens.HERO_HEIGHT), MATCH))
            addView(text, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    } else {
        column().apply {
            addView(cover(course, dp(Dimens.SHELF_HEIGHT)), LinearLayout.LayoutParams(MATCH, dp(Dimens.SHELF_HEIGHT)))
            addView(text)
        }
    }
    return card.asCard(clickable = true).apply {
        clipToOutline = true
        setOnClickListener { kit.onCourse(course) }
    }
}

private fun Context.heroText(kit: BrowseKit, course: Course, line: String): View = column().apply {
    val facts = kit.book.facts(course)
    softBackground(course, radius = 0)
    setPadding(dp(Dimens.SPACE_6), dp(Dimens.SPACE_4), dp(Dimens.SPACE_6), dp(Dimens.SPACE_4))
    addView(label(line, Dimens.SMALL).atMostLines(1))
    val name = label(course.name, Dimens.TITLE, bold = true).atMostLines(1)
    if (kit.wide) {
        val title = row().apply {
            addView(name, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(Dimens.SPACE_4) })
            addView(pickRow(course.tags))
        }
        addView(title)
    } else {
        addView(name)
        addSpaced(pickRow(course.tags), Dimens.SPACE_2)
    }
    val numbers = listOf(
        Fact("${facts.curves}", "굽이"),
        Fact(ratioText(facts), "에움 배율"),
        Fact(climbText(course, facts), "오르막"),
    )
    addSpaced(factRow(numbers, Dimens.VALUE), Dimens.SPACE_2)
    elevationChart(course, kit.book.sectionMeters(course))?.let { chart ->
        addSpaced(moodLegend(facts.moodShare), Dimens.SPACE_2)
        val size = if (kit.wide) fill() else LinearLayout.LayoutParams(MATCH, dp(Dimens.HERO_WELL_HEIGHT))
        addView(chart, size.apply { topMargin = dp(Dimens.SPACE_2) })
    }
}

/** 고도 그래프의 색 범례. 구간이 많아도 줄이 넘치지 않도록 구간이 아니라 분위기별로 묶는다. */
private fun Context.moodLegend(share: Map<String, Int>): View = row().apply {
    share.forEach { (mood, percent) ->
        addView(sectionDot(mood), LinearLayout.LayoutParams(dp(Dimens.DOT), dp(Dimens.DOT)).apply { marginEnd = dp(Dimens.SPACE_2) })
        addView(
            label("${moodWord(mood)} $percent%", Dimens.LABEL).tabular(),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(Dimens.SPACE_6) },
        )
    }
}

private fun Context.topCurves(kit: BrowseKit, onRanking: () -> Unit): View = column().apply {
    asCard()
    // 터치 76dp 를 지키는 제목 줄과 88dp 썸네일 세 줄이 카드 높이에 들어가도록 위아래를 줄인다.
    setPadding(dp(Dimens.SPACE_6), dp(Dimens.SPACE_3), dp(Dimens.SPACE_6), dp(Dimens.SPACE_3))
    val title = row().apply {
        addView(heading("굽이 많은 길"), LinearLayout.LayoutParams(0, WRAP, 1f))
        val all = label("랭킹 전체", Dimens.SMALL, palette.accent).apply {
            gravity = Gravity.CENTER
            minHeight = dp(Dimens.TOUCH_MIN)
            setOnClickListener { onRanking() }
        }
        addView(all, LinearLayout.LayoutParams(WRAP, dp(Dimens.TOUCH_MIN)))
    }
    addView(title)
    kit.book.ranked(RankBy.CURVES).take(TOP_COUNT).forEachIndexed { i, ranked ->
        addView(topRow(i + 1, ranked) { kit.onCourse(ranked.course) }, fill().apply { topMargin = dp(Dimens.SPACE_2) })
    }
}

private fun Context.topRow(place: Int, ranked: Ranked, onClick: () -> Unit): View = row().apply {
    minimumHeight = dp(Dimens.THUMB)
    background = pressable(palette.bgRaised, palette.bgPressed, Dimens.RADIUS_SM)
    setOnClickListener { onClick() }
    addView(
        label("$place", Dimens.HEADING, if (place == 1) palette.accent else palette.ink, bold = true).tabular(),
        LinearLayout.LayoutParams(dp(Dimens.SPACE_10), WRAP),
    )
    addView(
        cover(ranked.course, dp(Dimens.THUMB)),
        LinearLayout.LayoutParams(dp(Dimens.THUMB), dp(Dimens.THUMB)).apply { marginEnd = dp(Dimens.SPACE_4) },
    )
    val text = column().apply {
        addView(label(ranked.course.name, Dimens.BODY).atMostLines(1))
        addView(label(pickLabels(ranked.course.tags).firstOrNull().orEmpty(), Dimens.LABEL, palette.inkMuted).atMostLines(1))
    }
    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_2) })
    addView(label("${ranked.facts.curves}", Dimens.HEADING, bold = true).tabular())
}

private fun Context.themeShelf(kit: BrowseKit, onTheme: (String) -> Unit): View = column().apply {
    addView(heading("테마로 고르기"))
    val tiles = kit.book.themes.map { themeTile(it) { onTheme(it.key) } }
    addSpaced(grid(tiles, columns = if (kit.wide) 6 else 3, rowHeight = dp(Dimens.THEME_TILE_HEIGHT)), Dimens.SPACE_3)
}

private fun Context.themeTile(shelf: Shelf, onClick: () -> Unit): View = row().apply {
    val mood = themeMood(shelf.key)
    background = pressable(palette.airColor(mood), palette.bgPressed)
    setPadding(dp(Dimens.SPACE_6), 0, dp(Dimens.SPACE_6), 0)
    setOnClickListener { onClick() }
    addView(
        label(themeLabel(shelf.key).orEmpty(), Dimens.BODY, palette.moodColor(mood), bold = true).atMostLines(1),
        LinearLayout.LayoutParams(0, WRAP, 1f),
    )
    addView(label("${shelf.count}", Dimens.SMALL, palette.inkSecondary).tabular())
}

/** 지역이 정해진 코스가 없으면 묶음을 두지 않는다. */
private fun Context.regionShelf(kit: BrowseKit, selected: String?, onRegion: (String) -> Unit): View? {
    val regions = kit.book.regions
    if (regions.isEmpty()) return null
    val chosen = selected?.takeIf { key -> regions.any { it.key == key } } ?: regions.first().key
    val covers = FrameLayout(this)
    fun show(region: String) {
        val cells = inRegion(kit.book.courses, region).map { coverCard(kit, it, dp(Dimens.SHELF_HEIGHT)) }
        covers.removeAllViews()
        covers.addView(coverRow(cells, dp(kit.cellWidth(if (kit.wide) 5 else 2)), dp(Dimens.SHELF_HEIGHT)))
    }
    show(chosen)
    val chips = chipRow(regions.map { Choice(it.key, it.key) }, chosen, Dimens.SMALL) { region ->
        onRegion(region)
        show(region)
    }
    return column().apply {
        if (kit.wide) {
            val top = row().apply {
                addView(heading("지역별 추천"), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(Dimens.SPACE_6) })
                addView(chips, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            addView(top)
        } else {
            addView(heading("지역별 추천"))
            addSpaced(chips, Dimens.SPACE_3)
        }
        addSpaced(covers, Dimens.SPACE_3)
    }
}
