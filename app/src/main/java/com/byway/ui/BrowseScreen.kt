package com.byway.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.byway.core.course.Course
import com.byway.core.course.CourseFilter
import com.byway.core.course.Found
import com.byway.core.course.filterCourses
import com.byway.service.BrowseLocation
import com.byway.service.DriveBoard
import com.byway.service.ShareSync
import com.byway.ui.share.OFFLINE_NOTE
import com.byway.ui.share.codeEntry
import com.byway.ui.share.finishesText
import com.byway.ui.share.isShared
import com.byway.ui.share.sharedInfo

/**
 * 더 많은 코스(더 많은 코스 설계 5절). 검색 칸, 걸러 보기 줄, 코스 카드는 운전석 쪽인 왼쪽, 한국 지도는 오른쪽이다.
 * 카드를 누르면 지도가 그 코스로 확대된다. 카드는 보이는 것만 만드는 격자라 165장이어도 바로 뜬다.
 */
internal fun browseScreen(context: Context, kit: BrowseKit, nav: Nav, update: (Nav) -> Unit, go: (Nav) -> Unit): View = with(context) {
    val third = kit.window == WindowClass.THIRD
    val panel = when (kit.window) {
        WindowClass.FULL -> Dimens.PANEL_WIDTH_FULL
        else -> Dimens.PANEL_WIDTH
    }
    val here = BrowseLocation.last ?: DriveBoard.state.position
    fun find(filter: CourseFilter): List<Found> =
        filterCourses(kit.book.courses, filter, here, { kit.book.facts(it).meters }, ::searchWords)
    var found = find(nav.filter)
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
        val shown = found.map { it.course }
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
        val cards = CardAdapter(context, kit, panel, current.filter.near != null)
        cards.submit(found)
        fun mark(id: String?) {
            cards.select(id)
            val course = found.firstOrNull { it.course.id == id }?.course
            detail.visibility = if (course == null) View.GONE else View.VISIBLE
            atlas.setNote(if (course?.dummy == true) ATLAS_DUMMY_NOTE else null, if (third) 0 else panelEnd)
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
                detail.text = "${withDummyMark(course, course.name)} 자세히 보기"
                detail.setOnClickListener { kit.onCourse(course) }
            }
        }
        fun pick(id: String) {
            val camera = atlas.cameraFor(id) ?: return
            keep(current.copy(picked = id, atlas = camera))
            atlas.focusOn(camera, id)
            mark(id)
        }
        cards.onPick = ::pick
        atlas.onPick = ::pick
        atlas.onCamera = { keep(current.copy(atlas = it)) }
        atlas.addChrome(if (third) 0 else panelEnd, nav.filter.region ?: "전국") {
            val camera = atlas.overview()
            keep(current.copy(picked = null, atlas = camera))
            atlas.focusOn(camera, null)
            mark(null)
        }

        val count = label("코스 ${found.size}개", Dimens.SMALL, palette.inkSecondary)
        val empty = column().apply {
            addView(label(NOTHING_FOUND, Dimens.BODY, palette.inkSecondary))
            addSpaced(quietButton(CLEAR_FILTERS) { go(current.copy(filter = CourseFilter(), openFilter = null, picked = null, atlas = null)) }, Dimens.SPACE_3)
        }
        val grid = cards.grid()
        fun showFound() {
            count.text = "코스 ${found.size}개"
            empty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
            grid.visibility = if (found.isEmpty()) View.GONE else View.VISIBLE
        }
        // 검색은 화면을 다시 만들지 않는다. 목록, 수, 지도만 바꿔 키보드와 초점이 남는다.
        val search = searchField(nav.filter.query) { text ->
            if (text == current.filter.query) return@searchField
            keep(current.copy(filter = current.filter.copy(query = text), picked = null, atlas = null))
            found = find(current.filter)
            cards.submit(found)
            showFound()
            atlas.show(Tab.BROWSE, kit.window, found.map { it.course }, null, null, night, shade)
            mark(null)
        }

        val body = column().apply {
            isClickable = true
            val edge = dp(if (third) Dimens.SPACE_6 else Dimens.SPACE_8)
            setPadding(edge, if (third) dp(Dimens.SPACE_3) else edge, edge, if (third) dp(Dimens.SPACE_6) else edge)
            if (!third) {
                addView(count)
                addView(label("어느 길을 달려 볼까요", Dimens.TITLE, bold = true).atMostLines(1))
            }
            addSpaced(search, if (third) 0 else Dimens.SPACE_3)
            // 공유 서버(공유 설계 6-4). 받으면 그 코스를 홈에 올린다.
            addSpaced(codeEntry(kit.onCourse), Dimens.SPACE_3)
            if (ShareSync.offline) addSpaced(label(OFFLINE_NOTE, Dimens.LABEL, palette.inkSecondary).atMostLines(2), Dimens.SPACE_2)
            val filter = nav.filter
            addSpaced(filterBar(filter, nav.openFilter, { open -> go(current.copy(openFilter = open)) }) {
                go(current.copy(filter = CourseFilter(query = current.filter.query), openFilter = null, picked = null, atlas = null))
            }, Dimens.SPACE_3)
            nearNote(filter, nav.openFilter, here != null)?.let { addSpaced(label(it, Dimens.LABEL, palette.inkSecondary).atMostLines(1), Dimens.SPACE_2) }
            nav.openFilter?.let { kind ->
                val options = filterOptions(kind, filter, here != null, { find(it).size }) { next ->
                    go(current.copy(filter = next.copy(query = current.filter.query), openFilter = null, picked = null, atlas = null))
                }
                addSpaced(options, Dimens.SPACE_2)
            }
            addView(empty, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(Dimens.SPACE_6) })
            addView(grid, fill().apply { topMargin = dp(Dimens.SPACE_3) })
            addView(detail, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(Dimens.SPACE_3) })
        }
        showFound()
        val bodySize = if (third) {
            FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = dp(Dimens.THIRD_ATLAS_HEIGHT) }
        } else {
            FrameLayout.LayoutParams(dp(panel), MATCH).apply { leftMargin = dp(Dimens.RAIL_WIDTH) }
        }
        addView(body, bodySize)
        mark(picked)
    }
}

/**
 * 코스 목록(PM 11-11: 카드 대신 목록). 한 줄에 왼쪽 작은 지도, 오른쪽 이름 · 지역과 거리 · 설명.
 * 화면에 보이는 줄만 만들고 넘기면 다시 쓴다. 전체 화면은 두 열, 나머지는 한 열이다.
 */
private class CardAdapter(private val context: Context, private val kit: BrowseKit, @Suppress("UNUSED_PARAMETER") panel: Int, private val showAway: Boolean) : BaseAdapter() {
    private val columns = if (kit.window == WindowClass.FULL) 2 else 1
    private val shared = context.sharedInfo()
    private var items: List<Found> = emptyList()
    private var selected: String? = null
    var onPick: (String) -> Unit = {}

    fun submit(next: List<Found>) {
        items = next
        notifyDataSetChanged()
    }

    fun select(id: String?) {
        selected = id
        notifyDataSetChanged()
    }

    fun grid(): GridView = GridView(context).apply {
        numColumns = columns
        horizontalSpacing = context.dp(Dimens.SPACE_3)
        verticalSpacing = context.dp(Dimens.SPACE_3)
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        isVerticalScrollBarEnabled = false
        selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        overScrollMode = View.OVER_SCROLL_NEVER
        adapter = this@CardAdapter
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = (convertView as? Row) ?: Row(context).apply {
            // 설명 두 줄이 다 보이게 높이는 글에 맞추고, 작은 지도보다 낮아지지는 않는다.
            layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT)
            minimumHeight = context.dp(THUMB_HEIGHT + 2 * Dimens.SPACE_3)
        }
        row.bind(items[position])
        return row
    }

    private inner class Row(context: Context) : LinearLayout(context) {
        private val cover = FrameLayout(context)
        private val name: TextView = context.label("", Dimens.SMALL, bold = true).atMostLines(1)
        private val line: TextView = context.label("", Dimens.LABEL, context.palette.inkSecondary).atMostLines(1)
        // 2열(전체 화면)에서 칸 높이가 같아야 다음 줄이 앞 칸을 덮지 않는다. 설명은 늘 두 줄 자리를 쓴다(검토 I-1).
        private val about: TextView = context.label("", Dimens.LABEL).fixedLines(2)
        private var id: String? = null

        init {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val pad = context.dp(Dimens.SPACE_3)
            setPadding(pad, pad, pad, pad)
            pressMotion()
            setOnClickListener { id?.let(onPick) }
            addView(cover, LayoutParams(context.dp(THUMB_WIDTH), context.dp(THUMB_HEIGHT)))
            addView(context.column().apply {
                addView(name)
                addView(line)
                addView(about, LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(Dimens.SPACE_2) })
            }, LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(Dimens.SPACE_4) })
        }

        fun bind(item: Found) {
            val course = item.course
            background = if (course.id == selected) {
                context.rounded(context.palette.bgRaised, Dimens.RADIUS_MD, context.palette.accent)
            } else {
                context.rounded(context.palette.bgRaised, Dimens.RADIUS_MD)
            }
            if (id != course.id) {
                id = course.id
                cover.removeAllViews()
                cover.addView(context.cover(course, context.dp(THUMB_WIDTH)), matchFrame())
            }
            name.text = course.name
            line.text = rowText(course, item.awayMeters)
            about.text = course.summary
            about.visibility = if (course.summary.isBlank()) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun rowText(course: Course, away: Double?): String {
        val meters = kit.book.facts(course).meters
        if (!isShared(course.id)) return rowLine(course, meters, away, showAway)
        return listOf("공유", kmText(meters), finishesText(shared[course.id]?.finishes ?: 0)).joinToString(" · ")
    }

    private companion object {
        /** 작은 지도 칸(dp). 카드 그림과 같은 16:10 이다. */
        const val THUMB_HEIGHT = 92
        const val THUMB_WIDTH = THUMB_HEIGHT * 16 / 10
    }
}
