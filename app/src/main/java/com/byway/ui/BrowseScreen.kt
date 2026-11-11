package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.course.inRegion
import com.byway.core.course.withTheme
import com.byway.service.ShareSync
import com.byway.ui.share.OFFLINE_NOTE
import com.byway.ui.share.codeEntry
import com.byway.ui.share.finishesText
import com.byway.ui.share.isShared
import com.byway.ui.share.sharedInfo

private const val ALL = "전체"
private val REGIONS = listOf("수도권", "강원", "충청", "경상", "전라", "남해안", "제주")
private val THEMES = listOf("coast" to "바다", "mountain" to "고개", "river" to "강", "lake" to "호수", "forest" to "숲")

/** 둘러보기. 고르는 칩과 코스 카드는 운전석 쪽인 왼쪽, 한국 지도는 오른쪽이다. 카드를 누르면 지도가 그 코스로 확대된다. */
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
        val panelEnd = Dimens.RAIL_WIDTH + panel
        val shade = if (third) null else panelShade(panelEnd.toFloat())
        val picked = nav.picked?.takeIf { id -> shown.any { it.id == id } }
        atlas.show(Tab.BROWSE, kit.window, shown, picked, nav.atlas, night, shade)
        addView(atlas, FrameLayout.LayoutParams(MATCH, if (third) dp(Dimens.THIRD_ATLAS_HEIGHT) else MATCH))
        // 넓은 창에서 코스를 고르면 글 칸 뒤에 그 코스의 분위기(구간 색과 흐린 사진)를 깐다. 지도 가림과 같은 띠에서 옅어지고,
        // 84%로 깔아 지도가 글 칸 아래에 16%로 비치는 것은 그대로다(화면 다듬기 설계 1·4절).
        val mood = DriveBackdrop(context, kit.window, band = FadeBand(false, panelEnd - Dimens.SPACE_8.toFloat(), panelEnd + 50f, 0f)).apply {
            alpha = 1f - MAP_UNDER_TEXT
            visibility = View.GONE
        }
        if (!third) addView(mood, matchFrame())

        val detail = primaryButton("") {}
        val cards = mutableMapOf<String, View>()
        fun mark(id: String?) {
            cards.forEach { (key, card) -> card.background = cardFace(key == id) }
            val course = shown.firstOrNull { it.id == id }
            detail.visibility = if (course == null) View.GONE else View.VISIBLE
            if (!third) {
                mood.visibility = if (course == null) View.GONE else View.VISIBLE
                atlas.setShadeOn(course == null)
                mood.clearPicture()
                val photo = course != null && canSwipeToScenery(course.sceneryCredit, Covers.hasScenery(context, course))
                course?.let { mood.setTint(moodTint(it, 0, palette)) }
                if (photo && course != null) Covers.loadScenery(context, course) { if (mood.visibility == View.VISIBLE) mood.setPicture(it) }
                atlas.setPhotoCredit(course?.let { moodPhotoCredit(it, photo) })
            }
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
        atlas.addChrome(if (third) 0 else panelEnd, nav.region ?: "전국") {
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
                addView(label("코스 ${shown.size}개", Dimens.SMALL, palette.inkSecondary))
                addView(label("어느 길을 달려 볼까요", Dimens.TITLE, bold = true).atMostLines(1))
            }
            // 공유 서버(공유 설계 6-4). 받으면 그 코스를 홈에 올린다.
            addSpaced(codeEntry(kit.onCourse), Dimens.SPACE_3)
            if (ShareSync.offline) addSpaced(label(OFFLINE_NOTE, Dimens.LABEL, palette.inkSecondary).atMostLines(2), Dimens.SPACE_2)
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
            FrameLayout.LayoutParams(dp(panel), MATCH).apply { leftMargin = dp(Dimens.RAIL_WIDTH) }
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
    val shared = sharedInfo()
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
                val meters = kit.book.facts(course).meters
                val line = if (isShared(course.id)) {
                    listOf("공유", kmText(meters), finishesText(shared[course.id]?.finishes ?: 0)).joinToString(" · ")
                } else {
                    courseLine(course.region, meters, pickLabels(course.tags).firstOrNull().orEmpty())
                }
                addView(label(withDummyMark(course, line), Dimens.LABEL, palette.inkSecondary).atMostLines(1))
            }
            addView(text)
        }
    }
    return grid(cells, columns, dp(picture + Dimens.MAP_CARD_TEXT))
}
