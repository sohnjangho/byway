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

private const val HERO_HEIGHT = 392
private const val HERO_WEIGHT = 1030f
private const val TOP_LIST_WEIGHT = 548f
private const val TOP_COUNT = 3
private const val THUMB = 88
private const val SHELF_HEIGHT = 340

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
    val body = column().apply {
        setPadding(dp(kit.edge), dp(Look.GAP), dp(kit.edge), dp(Look.WIDE_GAP))
        if (kit.wide) {
            val first = LinearLayout(context).apply {
                addView(hero, LinearLayout.LayoutParams(0, MATCH, HERO_WEIGHT).apply { marginEnd = dp(Look.GAP) })
                addView(top, LinearLayout.LayoutParams(0, MATCH, TOP_LIST_WEIGHT))
            }
            addView(first, LinearLayout.LayoutParams(MATCH, dp(HERO_HEIGHT)))
        } else {
            addView(hero)
            addSpaced(top, Look.GAP)
        }
        addSpaced(themeShelf(kit, onTheme), Look.GAP)
        regionShelf(kit, region, onRegion)?.let { addSpaced(it, Look.GAP) }
    }
    ScrollView(context).apply { addView(body) }
}

private fun Context.heading(text: String) = label(text, Look.HEADING, bold = true)

private fun Context.heroCard(kit: BrowseKit, course: Course, line: String): View {
    val facts = kit.book.facts(course)
    val text = column().apply {
        setPadding(dp(32), dp(Look.GAP), dp(32), dp(Look.GAP))
        addView(label(line, Look.SMALL, Look.ACCENT).atMostLines(1))
        addView(label(course.name, Look.TITLE, bold = true).atMostLines(1))
        addView(label(kit.book.character(course), Look.SMALL, Look.MUTED).atMostLines(1))
        val numbers = listOf(
            Fact("${facts.curves}", "굽이"),
            Fact(ratioText(facts), "에움 배율"),
            Fact(climbText(course, facts), "오르막"),
        )
        addSpaced(factRow(numbers, Look.VALUE), 8)
        val shape = ElevationView(context).apply { silhouette(course.elevation) }
        addView(shape, fill().apply { topMargin = dp(8) })
    }
    val card = if (kit.wide) {
        LinearLayout(this).apply {
            addView(cover(course, dp(HERO_HEIGHT)), LinearLayout.LayoutParams(dp(HERO_HEIGHT), MATCH))
            addView(text, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
    } else {
        column().apply {
            addView(cover(course, dp(SHELF_HEIGHT)), LinearLayout.LayoutParams(MATCH, dp(SHELF_HEIGHT)))
            addView(text, LinearLayout.LayoutParams(MATCH, dp(360)))
        }
    }
    return card.apply {
        background = pressable(Look.SURFACE, Look.SURFACE_PRESSED)
        clipToOutline = true
        setOnClickListener { kit.onCourse(course) }
    }
}

private fun Context.topCurves(kit: BrowseKit, onRanking: () -> Unit): View = column().apply {
    background = rounded(Look.SURFACE)
    // 터치 76dp 를 지키는 제목 줄과 88dp 썸네일 세 줄이 392dp 카드에 들어가도록 위아래를 줄인다.
    setPadding(dp(28), dp(12), dp(28), dp(12))
    val title = row().apply {
        addView(heading("굽이 많은 길"), LinearLayout.LayoutParams(0, WRAP, 1f))
        val all = label("랭킹 전체", Look.SMALL, Look.ACCENT).apply {
            gravity = Gravity.CENTER
            minHeight = dp(Look.TOUCH_MIN)
            setOnClickListener { onRanking() }
        }
        addView(all, LinearLayout.LayoutParams(WRAP, dp(Look.TOUCH_MIN)))
    }
    addView(title)
    kit.book.ranked(RankBy.CURVES).take(TOP_COUNT).forEachIndexed { i, ranked ->
        addView(topRow(i + 1, ranked) { kit.onCourse(ranked.course) }, fill().apply { topMargin = dp(8) })
    }
}

private fun Context.topRow(place: Int, ranked: Ranked, onClick: () -> Unit): View = row().apply {
    minimumHeight = dp(THUMB)
    background = pressable(Look.SURFACE, Look.SURFACE_PRESSED, Look.RADIUS_SM)
    setOnClickListener { onClick() }
    addView(
        label("$place", Look.HEADING, if (place == 1) Look.ACCENT else Look.INK, bold = true).tabular(),
        LinearLayout.LayoutParams(dp(44), WRAP),
    )
    addView(cover(ranked.course, dp(THUMB)), LinearLayout.LayoutParams(dp(THUMB), dp(THUMB)).apply { marginEnd = dp(16) })
    addView(label(ranked.course.name, Look.BODY).atMostLines(1), LinearLayout.LayoutParams(0, WRAP, 1f))
    addView(label("${ranked.facts.curves}", Look.SMALL, Look.MUTED).tabular())
}

private fun Context.themeShelf(kit: BrowseKit, onTheme: (String) -> Unit): View = column().apply {
    addView(heading("테마로 고르기"))
    val tiles = kit.book.themes.map { themeTile(it) { onTheme(it.key) } }
    addSpaced(grid(tiles, columns = if (kit.wide) 6 else 3, rowHeight = dp(104)), 12)
}

private fun Context.themeTile(shelf: Shelf, onClick: () -> Unit): View = row().apply {
    val mood = themeMood(shelf.key)
    background = pressable(airColor(mood), Look.SURFACE_PRESSED)
    setPadding(dp(Look.GAP), 0, dp(Look.GAP), 0)
    setOnClickListener { onClick() }
    addView(
        label(themeLabel(shelf.key).orEmpty(), Look.BODY, moodColor(mood), bold = true).atMostLines(1),
        LinearLayout.LayoutParams(0, WRAP, 1f),
    )
    addView(label("${shelf.count}", Look.SMALL, Look.MUTED).tabular())
}

/** 지역이 정해진 코스가 없으면 묶음을 두지 않는다. */
private fun Context.regionShelf(kit: BrowseKit, selected: String?, onRegion: (String) -> Unit): View? {
    val regions = kit.book.regions
    if (regions.isEmpty()) return null
    val chosen = selected?.takeIf { key -> regions.any { it.key == key } } ?: regions.first().key
    val covers = FrameLayout(this)
    fun show(region: String) {
        val cells = inRegion(kit.book.courses, region).map { course ->
            coverCard(course, kit.book.character(course), dp(SHELF_HEIGHT)) { kit.onCourse(course) }
        }
        covers.removeAllViews()
        covers.addView(coverRow(cells, dp(kit.cellWidth(if (kit.wide) 5 else 2)), dp(SHELF_HEIGHT)))
    }
    show(chosen)
    val chips = chipRow(regions.map { Choice(it.key, it.key) }, chosen, Look.SMALL) { region ->
        onRegion(region)
        show(region)
    }
    return column().apply {
        if (kit.wide) {
            val top = row().apply {
                addView(heading("지역별 추천"), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(Look.GAP) })
                addView(chips, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            addView(top)
        } else {
            addView(heading("지역별 추천"))
            addSpaced(chips, 12)
        }
        addSpaced(covers, 12)
    }
}
