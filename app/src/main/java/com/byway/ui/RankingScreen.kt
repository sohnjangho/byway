package com.byway.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.course.Px
import com.byway.core.course.RankBy
import com.byway.core.course.Ranked
import com.byway.service.SceneCatalog

private const val DOT_RADIUS = 8f
private const val DOT_RING = 3f

fun rankHead(by: RankBy): String = when (by) {
    RankBy.CURVES -> "굽이가 가장 많은 길"
    RankBy.EUM -> "가장 크게 돌아가는 길"
    RankBy.CLIMB -> "가장 많이 오르는 길"
    RankBy.LENGTH -> "가장 긴 길"
}

/** 랭킹. 왼쪽은 순위와 작은 한국 지도, 오른쪽은 고른 코스의 장면이 칸 없이 이어진다. 코스에서 계산한 값만 쓴다. */
internal fun rankingScreen(context: Context, kit: BrowseKit, nav: Nav, update: (Nav) -> Unit, go: (Nav) -> Unit): View = with(context) {
    val ranked = kit.book.ranked(nav.rankBy)
    val third = kit.window == WindowClass.THIRD
    val full = kit.window == WindowClass.FULL
    var current = nav
    var chosen = ranked.firstOrNull { it.course.id == nav.picked } ?: ranked.first()

    FrameLayout(this).apply {
        setBackgroundColor(palette.bg)
        val sceneView = SceneView(context, kit.window, list = true)
        addView(sceneView, FrameLayout.LayoutParams(MATCH, if (third) dp(Dimens.THIRD_LIST_SCENE_HEIGHT) else MATCH))
        val soft = SoftPatch(context, third)
        val softSize = if (third) {
            FrameLayout.LayoutParams(MATCH, dp(Dimens.THIRD_SOFT_HEIGHT), Gravity.BOTTOM)
        } else {
            FrameLayout.LayoutParams(dp(if (full) Dimens.SOFT_WIDTH_FULL else Dimens.SOFT_WIDTH), dp(Dimens.SOFT_HEIGHT)).apply { leftMargin = dp(Dimens.RAIL_WIDTH) }
        }
        addView(soft, softSize)

        val locator = Locator(context)
        val where = label("", Dimens.SMALL, bold = true)
        val whereLine = label("", Dimens.LABEL, palette.ink)
        val detail = quietButton("") { kit.onCourse(chosen.course) }
        val rows = mutableMapOf<String, View>()

        fun paintFor(item: Ranked, reveal: Boolean = false) {
            val course = item.course
            val scene = SceneCatalog.of(context, course.id)
            sceneView.show(course, scene, emptyList(), night, canSwipeToScenery(course.sceneryCredit, Covers.hasScenery(context, course)), reveal)
            Covers.loadSoft(context, course.id) { soft.setPicture(it) }
            scene?.let { locator.place(Px(it.atlas.x / SceneCatalog.index(context).koreaSize.x, it.atlas.y / SceneCatalog.index(context).koreaSize.y)) }
            where.text = course.region
            whereLine.text = "${kmText(item.facts.meters)} · 굽이 ${item.facts.curves}개"
            detail.text = "${course.name} 자세히 보기"
            rows.forEach { (id, row) -> row.background = if (id == course.id) rounded(palette.accentSoft) else null }
        }
        fun select(item: Ranked) {
            chosen = item
            current = current.copy(picked = item.course.id)
            update(current)
            paintFor(item, reveal = true)
        }

        val side = column().apply {
            isClickable = true
            val edge = dp(if (third) Dimens.SPACE_6 else Dimens.SPACE_8)
            setPadding(edge, if (third) dp(Dimens.SPACE_2) else edge, edge, if (third) dp(Dimens.SPACE_6) else dp(Dimens.SPACE_4))
            if (!third) {
                addView(label("랭킹 · 코스 선에서 잰 값", Dimens.SMALL, palette.accent))
                addView(label(rankHead(nav.rankBy), Dimens.TITLE, bold = true).atMostLines(1))
            }
            val tabs = RankBy.entries.map { Choice(it.name, rankTitle(it)) }
            addSpaced(chipRow(tabs, nav.rankBy.name, Dimens.SMALL) { key -> go(nav.copy(rankBy = RankBy.valueOf(key), picked = null)) }, Dimens.SPACE_2)
            val list = rankRows(kit, ranked, nav.rankBy, rows, ::select)
            addView(
                ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    addView(list)
                }.bounceAtEnds(vertical = true),
                fill().apply { topMargin = dp(Dimens.SPACE_2) },
            )
            if (!third) {
                val place = row().apply {
                    addView(locator, LinearLayout.LayoutParams(dp(Dimens.LOCATOR_WIDTH), dp(Dimens.LOCATOR_HEIGHT)).apply { marginEnd = dp(Dimens.SPACE_4) })
                    val text = column().apply {
                        addView(where)
                        addView(whereLine)
                        addView(label("추천 표시는 에움길이 고른 것입니다", Dimens.LABEL, palette.ink).atMostLines(1))
                    }
                    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
                }
                addSpaced(place, Dimens.SPACE_3)
            }
            addView(detail, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(Dimens.SPACE_3) })
        }
        val sideSize = if (third) {
            FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = dp(Dimens.THIRD_LIST_SCENE_HEIGHT) }
        } else {
            FrameLayout.LayoutParams(dp(if (full) Dimens.SIDE_WIDTH_FULL else Dimens.SIDE_WIDTH), MATCH).apply { leftMargin = dp(Dimens.RAIL_WIDTH) }
        }
        addView(side, sideSize)
        paintFor(chosen)
    }
}

private fun Context.rankRows(kit: BrowseKit, ranked: List<Ranked>, by: RankBy, rows: MutableMap<String, View>, onPick: (Ranked) -> Unit): View {
    val third = kit.window == WindowClass.THIRD
    val cells = ranked.mapIndexed { i, item ->
        row().apply {
            val inset = dp(Dimens.SPACE_2)
            setPadding(inset, inset, inset, inset)
            rows[item.course.id] = this
            pressMotion()
            setOnClickListener { onPick(item) }
            addView(
                label("${i + 1}", Dimens.HEADING, if (i == 0) palette.accent else palette.ink, bold = true).tabular().apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(dp(Dimens.RANK_NUMBER), WRAP).apply { marginEnd = dp(Dimens.SPACE_3) },
            )
            if (!third) {
                addView(cover(item.course, dp(Dimens.RANK_THUMB_W)), LinearLayout.LayoutParams(dp(Dimens.RANK_THUMB_W), dp(Dimens.RANK_THUMB_H)).apply { marginEnd = dp(Dimens.SPACE_3) })
            }
            val text = column().apply {
                addView(label(item.course.name, Dimens.SMALL, bold = true).atMostLines(1))
                val note = (listOf(item.course.region) + pickLabels(item.course.tags).take(2)).filter { it.isNotBlank() }.joinToString(" · ")
                addView(label(note, Dimens.LABEL, palette.ink).atMostLines(1))
            }
            addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(label(rankValue(by, item), Dimens.HEADING, bold = true).tabular())
        }
    }
    return if (kit.window == WindowClass.FULL) {
        grid(cells, 2, dp(Dimens.RANK_ROW_HEIGHT))
    } else {
        column().apply { cells.forEachIndexed { i, cell -> addSpaced(cell, if (i == 0) 0 else Dimens.SPACE_2) } }
    }
}

/** 작은 한국 지도와 코스 위치 점. */
private class Locator(context: Context) : View(context) {
    private val colors = context.palette
    private var map: Bitmap? = null
    private var dot: Px? = null
    private val picture = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        Covers.loadAtlas(context, context.night) {
            map = it
            invalidate()
        }
    }

    fun place(at: Px) {
        dot = at
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        map?.let { canvas.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(0f, 0f, width.toFloat(), height.toFloat()), picture) }
        val at = dot ?: return
        val x = at.x * width
        val y = at.y * height
        fill.color = colors.photoScrim
        canvas.drawCircle(x, y, (DOT_RADIUS + DOT_RING) * density, fill)
        fill.color = colors.accent
        canvas.drawCircle(x, y, DOT_RADIUS * density, fill)
    }
}
