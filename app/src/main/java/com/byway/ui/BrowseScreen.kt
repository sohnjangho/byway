package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.course.inRegion
import com.byway.core.course.withTheme

private const val ALL = "전체"
private val REGIONS = listOf("수도권", "강원", "충청", "경상", "전라", "남해안", "제주")
private val THEMES = listOf("coast" to "바다", "mountain" to "고개", "river" to "강", "lake" to "호수", "forest" to "숲")

/** 둘러보기. 왼쪽 한국 지도, 오른쪽 코스 카드다. 카드를 누르면 지도가 그 코스로 확대된다. */
internal fun browseScreen(context: Context, kit: BrowseKit, nav: Nav, update: (Nav) -> Unit, go: (Nav) -> Unit): View = with(context) {
    val third = kit.window == WindowClass.THIRD
    val panel = when (kit.window) {
        WindowClass.FULL -> Dimens.PANEL_WIDTH_FULL
        else -> Dimens.PANEL_WIDTH
    }
    val shown = kit.book.courses
        .let { list -> nav.region?.let { inRegion(list, it) } ?: list }
        .let { list -> nav.theme?.let { withTheme(list, it) } ?: list }
    var current = nav
    fun keep(next: Nav) {
        current = next
        update(next)
    }

    FrameLayout(this).apply {
        setBackgroundColor(palette.bg)
        val atlas = AtlasView(context)
        val shade = if (third) null else AtlasShade(true, panel - 40f, panel + 140f)
        val picked = nav.picked?.takeIf { id -> shown.any { it.id == id } }
        atlas.show(Tab.BROWSE, kit.window, shown, picked, nav.atlas, night, shade)
        addView(atlas, FrameLayout.LayoutParams(MATCH, if (third) dp(Dimens.THIRD_ATLAS_HEIGHT) else MATCH))

        val detail = primaryButton("") {}
        val cards = mutableMapOf<String, View>()
        fun mark(id: String?) {
            cards.forEach { (key, card) -> card.background = cardFace(key == id) }
            val course = shown.firstOrNull { it.id == id }
            detail.visibility = if (course == null) View.GONE else View.VISIBLE
            if (course != null) {
                detail.text = "${course.name} 자세히 보기"
                detail.setOnClickListener { kit.onCourse(course) }
            }
        }
        fun pick(id: String) {
            val camera = atlas.cameraFor(id) ?: return
            keep(current.copy(picked = id, atlas = camera))
            atlas.focusOn(camera, id)
            mark(id)
        }
        atlas.onPick = ::pick
        atlas.onCamera = { keep(current.copy(atlas = it)) }
        atlas.addChrome(if (third) 0 else panel, nav.region ?: "전국") {
            val camera = atlas.overview()
            keep(current.copy(picked = null, atlas = camera))
            atlas.focusOn(camera, null)
            mark(null)
        }

        val body = column().apply {
            isClickable = true
            val edge = dp(if (third) Dimens.SPACE_6 else Dimens.SPACE_8)
            setPadding(edge, if (third) 0 else edge, edge, if (third) dp(Dimens.SPACE_6) else edge)
            if (!third) {
                addView(label("코스 ${shown.size}개", Dimens.SMALL, palette.accent))
                addView(label("어느 길을 달려 볼까요", Dimens.TITLE, bold = true).atMostLines(1))
            }
            addSpaced(regionChips(kit, nav, go), Dimens.SPACE_3)
            addSpaced(themeChips(kit, nav, go), Dimens.SPACE_2)
            val grid = cardGrid(kit, shown, panel, cards, ::pick)
            val scroll = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(grid)
            }.bounceAtEnds(vertical = true)
            addView(scroll, fill().apply { topMargin = dp(Dimens.SPACE_3) })
            addView(detail, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(Dimens.SPACE_3) })
        }
        val bodySize = if (third) {
            FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = dp(Dimens.THIRD_ATLAS_HEIGHT) }
        } else {
            FrameLayout.LayoutParams(dp(panel), MATCH, Gravity.END)
        }
        addView(body, bodySize)
        mark(picked)
    }
}

private fun Context.regionChips(kit: BrowseKit, nav: Nav, go: (Nav) -> Unit): View {
    val all = kit.book.courses
    val choices = (listOf(ALL) + REGIONS).map { region ->
        Choice(region, "$region ${if (region == ALL) all.size else inRegion(all, region).size}")
    }
    return chipRow(choices, nav.region ?: ALL, Dimens.SMALL) { key ->
        go(nav.copy(region = key.takeIf { it != ALL }, picked = null, atlas = null))
    }
}

private fun Context.themeChips(kit: BrowseKit, nav: Nav, go: (Nav) -> Unit): View {
    val all = kit.book.courses
    val choices = THEMES.map { (key, title) -> Choice(key, "$title ${withTheme(all, key).size}") }
    return chipRow(choices, nav.theme, Dimens.SMALL) { key ->
        go(nav.copy(theme = key.takeIf { it != nav.theme }, picked = null, atlas = null))
    }
}

private fun Context.cardFace(on: Boolean) =
    if (on) rounded(palette.bgRaised, Dimens.RADIUS_MD, palette.accent) else rounded(palette.bgRaised, Dimens.RADIUS_MD)

private fun Context.cardGrid(kit: BrowseKit, courses: List<Course>, panel: Int, cards: MutableMap<String, View>, onPick: (String) -> Unit): View {
    val columns = if (kit.window == WindowClass.FULL) 3 else 2
    val inner = (if (kit.window == WindowClass.THIRD) kit.widthDp else panel) - 2 * Dimens.SPACE_8
    val width = (inner - Dimens.SPACE_3 * (columns - 1)) / columns
    val picture = width * 10 / 16
    val cells = courses.map { course ->
        column().apply {
            clipToOutline = true
            cards[course.id] = this
            pressMotion()
            setOnClickListener { onPick(course.id) }
            addView(cover(course, dp(width)), LinearLayout.LayoutParams(MATCH, dp(picture)))
            val text = column().apply {
                setPadding(dp(Dimens.SPACE_3), dp(Dimens.SPACE_2), dp(Dimens.SPACE_3), dp(Dimens.SPACE_3))
                addView(label(course.name, Dimens.SMALL, bold = true).atMostLines(1))
                val line = listOf(course.region, kmText(kit.book.facts(course).meters), pickLabels(course.tags).firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                addView(label(line, Dimens.LABEL, palette.inkSecondary).atMostLines(1))
            }
            addView(text)
        }
    }
    return grid(cells, columns, dp(picture + Dimens.MAP_CARD_TEXT))
}
