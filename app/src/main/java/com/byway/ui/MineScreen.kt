package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.service.MyDrives

/** 내 코스. 큰 지도에 내 코스 번호와 지명이 있고, 코스를 고르면 경로와 설명이 나온다. 기록은 이 차에만 저장된다. */
internal fun mineScreen(
    context: Context,
    kit: BrowseKit,
    nav: Nav,
    update: (Nav) -> Unit,
    onStart: (String) -> Unit,
    onBrowse: () -> Unit,
): View = with(context) {
    val recent = MyDrives.recent(this)
    val courses = kit.book.mine(MyDrives.saved(this), recent)
    val third = kit.window == WindowClass.THIRD
    val full = kit.window == WindowClass.FULL
    var current = nav

    FrameLayout(this).apply {
        setBackgroundColor(palette.bg)
        val atlas = AtlasView(context)
        val shade = when {
            third -> null
            full -> AtlasShade(false, 1220f, 1440f)
            else -> AtlasShade(false, 700f, 920f)
        }
        val picked = nav.picked?.takeIf { id -> courses.any { it.id == id } }
        atlas.show(Tab.MINE, kit.window, courses, picked, nav.atlas, night, shade)
        addView(atlas, FrameLayout.LayoutParams(MATCH, if (third) dp(Dimens.THIRD_MINE_ATLAS_HEIGHT) else MATCH))

        val slot = FrameLayout(context)
        lateinit var showList: () -> Unit
        fun showCourse(id: String) {
            val course = courses.firstOrNull { it.id == id } ?: return
            slot.removeAllViews()
            slot.addView(courseCard(kit, course, recent, { showList() }, onStart), matchFrame())
        }
        fun pick(id: String) {
            val camera = atlas.cameraFor(id) ?: return
            current = current.copy(picked = id, atlas = camera)
            update(current)
            atlas.focusOn(camera, id)
            showCourse(id)
        }
        showList = {
            val camera = atlas.overview()
            current = current.copy(picked = null, atlas = camera)
            update(current)
            atlas.focusOn(camera, null)
            slot.removeAllViews()
            slot.addView(myList(courses, recent, ::pick, onBrowse), matchFrame())
        }
        atlas.onPick = ::pick
        atlas.onCamera = {
            current = current.copy(atlas = it)
            update(current)
        }
        atlas.addChrome(if (third) 0 else Dimens.RAIL_WIDTH + (if (full) Dimens.SIDE_WIDTH_FULL else Dimens.SIDE_WIDTH), "내 코스") { showList() }
        slot.addView(myList(courses, recent, ::pick, onBrowse), matchFrame())
        picked?.let(::showCourse)

        val side = column().apply {
            isClickable = true
            val edge = dp(if (third) Dimens.SPACE_6 else Dimens.SPACE_8)
            setPadding(edge, if (third) dp(Dimens.SPACE_2) else edge, edge, if (third) dp(Dimens.SPACE_6) else edge)
            addView(identity())
            addSpaced(totals(kit), Dimens.SPACE_4)
            addView(slot, fill().apply { topMargin = dp(Dimens.SPACE_4) })
        }
        val sideSize = if (third) {
            FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = dp(Dimens.THIRD_MINE_ATLAS_HEIGHT) }
        } else {
            FrameLayout.LayoutParams(dp(if (full) Dimens.SIDE_WIDTH_FULL else Dimens.SIDE_WIDTH), MATCH).apply { leftMargin = dp(Dimens.RAIL_WIDTH) }
        }
        addView(side, sideSize)
    }
}

private fun Context.identity(): View = row().apply {
    val mark = label("나", Dimens.HEADING, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(palette.bgPressed, Dimens.AVATAR_MEDIUM / 2)
    }
    addView(mark, LinearLayout.LayoutParams(dp(Dimens.AVATAR_MEDIUM), dp(Dimens.AVATAR_MEDIUM)).apply { marginEnd = dp(Dimens.SPACE_4) })
    val text = column().apply {
        addView(label("내 코스", Dimens.HEADING, bold = true))
        addView(label("기록은 이 차에만 저장됩니다", Dimens.LABEL, palette.inkSecondary).atMostLines(1))
    }
    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
}

private fun Context.totals(kit: BrowseKit): View {
    val totals = MyDrives.totals(this)
    val values = listOf(
        "${totals.finishedCourses}개" to "완주한 코스",
        kmText(totals.meters) to "달린 거리",
        "${totals.curves}개" to "지나온 굽이",
        "${MyDrives.saved(this).size}개" to "저장한 코스",
    )
    val cells = values.map { (value, name) ->
        column().apply {
            addView(label(withSmallUnit(value), Dimens.VALUE, bold = true).tabular().atMostLines(1))
            addView(label(name, Dimens.LABEL, palette.inkSecondary).atMostLines(1))
        }
    }
    val columns = if (kit.window == WindowClass.TWO) 2 else 4
    return grid(cells, columns, dp(Dimens.MINE_FACT_HEIGHT))
}

private fun Context.myList(courses: List<Course>, recent: List<String>, onPick: (String) -> Unit, onBrowse: () -> Unit): View {
    if (courses.isEmpty()) {
        return column().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("아직 달리거나 저장한 코스가 없습니다", Dimens.BODY))
            addSpaced(quietButton("코스 둘러보기", onBrowse), Dimens.SPACE_3)
        }
    }
    val list = column()
    courses.forEachIndexed { i, course ->
        val line = row().apply {
            minimumHeight = dp(Dimens.TOUCH_MIN)
            background = pressable(android.graphics.Color.TRANSPARENT, palette.bgPressed)
            val inset = dp(Dimens.SPACE_2)
            setPadding(inset, inset, inset, inset)
            pressMotion()
            setOnClickListener { onPick(course.id) }
            val number = label("${i + 1}", Dimens.LABEL, palette.onAccent, bold = true).tabular().apply {
                gravity = Gravity.CENTER
                background = rounded(palette.accent, Dimens.PLACE_NUMBER / 2)
            }
            addView(number, LinearLayout.LayoutParams(dp(Dimens.PLACE_NUMBER), dp(Dimens.PLACE_NUMBER)).apply { marginEnd = dp(Dimens.SPACE_3) })
            val text = column().apply {
                addView(label(course.name, Dimens.SMALL, bold = true).atMostLines(1))
                addView(label(if (course.id in recent) "달린 코스" else "저장한 코스", Dimens.LABEL, palette.inkSecondary).atMostLines(1))
            }
            addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        list.addSpaced(line, if (i == 0) 0 else Dimens.SPACE_2)
    }
    return ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(list)
    }.bounceAtEnds(vertical = true)
}

private fun Context.courseCard(kit: BrowseKit, course: Course, recent: List<String>, onBack: () -> Unit, onStart: (String) -> Unit): View = column().apply {
    background = rounded(palette.bgRaised)
    val edge = dp(Dimens.SPACE_6)
    setPadding(edge, dp(Dimens.SPACE_4), edge, dp(Dimens.SPACE_4))
    val facts = kit.book.facts(course)
    addView(label(if (course.id in recent) "달린 코스" else "저장한 코스", Dimens.LABEL).atMostLines(1))
    addView(label(course.name, Dimens.TITLE, bold = true).atMostLines(1))
    addSpaced(pickRow(course.tags), Dimens.SPACE_2)
    addSpaced(label(course.summary, Dimens.SMALL).atMostLines(3), Dimens.SPACE_2)
    addSpaced(label(listOf(course.region, kmText(facts.meters), "굽이 ${facts.curves}개", "오르막 ${climbText(course, facts)}").joinToString(" · "), Dimens.LABEL).atMostLines(1), Dimens.SPACE_2)
    addView(android.widget.Space(context), fill())
    val buttons = LinearLayout(context).apply {
        addView(quietButton("내 코스 전체", onBack), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
        addView(primaryButton("내비에 입히기") { onStart(course.id) }, LinearLayout.LayoutParams(0, WRAP, 1f))
    }
    addView(buttons)
}
