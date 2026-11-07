package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import com.byway.core.course.Course
import com.byway.service.MyDrives

private const val PER_ROW = 3

/** 프로필, 내 기록, 최근 코스, 저장한 코스, 설정. 이 차에 저장된 값만 보여 준다. */
internal fun mineScreen(context: Context, kit: BrowseKit, onBrowse: () -> Unit, onAbout: () -> Unit): View =
    with(context) {
        val profile = profileCard(kit, onAbout)
        val shelves = column().apply {
            addView(shelfTitle("최근 달린 코스"))
            addSpaced(recentShelf(kit), Dimens.SPACE_3)
            addSpaced(shelfTitle("저장한 코스"), Dimens.SPACE_6)
            addSpaced(savedShelf(kit, onBrowse), Dimens.SPACE_3)
        }
        val edge = dp(kit.edge)
        if (kit.wide) {
            LinearLayout(context).apply {
                setPadding(edge, dp(Dimens.SPACE_8), edge, dp(Dimens.SPACE_8))
                addView(profile, LinearLayout.LayoutParams(dp(Dimens.PROFILE_WIDTH), MATCH).apply { marginEnd = dp(Dimens.SPACE_8) })
                addView(ScrollView(context).apply { addView(shelves) }, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
        } else {
            val body = column().apply {
                setPadding(edge, dp(Dimens.SPACE_6), edge, dp(Dimens.SPACE_8))
                addView(profile)
                addSpaced(shelves, Dimens.SPACE_6)
            }
            ScrollView(context).apply { addView(body) }
        }
    }

private fun Context.shelfTitle(text: String) = label(text, Dimens.HEADING, bold = true)

private fun Context.courseShelf(kit: BrowseKit, courses: List<Course>): View {
    val cells = courses.map { coverCard(kit, it, dp(Dimens.MINE_SHELF_HEIGHT)) }
    val used = if (kit.wide) Dimens.PROFILE_WIDTH + Dimens.SPACE_8 else 0
    return coverRow(cells, dp(kit.cellWidth(if (kit.wide) PER_ROW else 2, used)), dp(Dimens.MINE_SHELF_HEIGHT))
}

private fun Context.recentShelf(kit: BrowseKit): View {
    val courses = kit.book.known(MyDrives.recent(this))
    return if (courses.isEmpty()) emptyNote("아직 끝까지 달린 코스가 없습니다") else courseShelf(kit, courses)
}

private fun Context.savedShelf(kit: BrowseKit, onBrowse: () -> Unit): View {
    val courses = kit.book.known(MyDrives.saved(this))
    if (courses.isNotEmpty()) return courseShelf(kit, courses)
    return column().apply {
        addView(emptyNote("저장한 코스가 없습니다"))
        addSpaced(quietButton("코스 고르러 가기", onBrowse), Dimens.SPACE_3)
    }
}

private fun Context.profileCard(kit: BrowseKit, onAbout: () -> Unit): View = column().apply {
    asCard()
    setPadding(dp(Dimens.SPACE_8), dp(Dimens.SPACE_8), dp(Dimens.SPACE_8), dp(Dimens.SPACE_8))
    addView(identity())
    addSpaced(record(), Dimens.SPACE_6)
    if (kit.wide) addView(Space(context), fill())
    addSpaced(toggleRow("차 안 연출", MyDrives.ambienceOn) { MyDrives.ambienceOn = it }, Dimens.SPACE_6)
    addSpaced(toggleRow("음성 안내", MyDrives.voiceOn) { MyDrives.voiceOn = it }, Dimens.SPACE_3)
    addSpaced(quietButton("앱 정보", onAbout), Dimens.SPACE_4)
}

private fun Context.identity(): View = row().apply {
    val mark = label("나", Dimens.VALUE, palette.accent, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(palette.bgPressed, Dimens.AVATAR_LARGE / 2)
    }
    addView(
        mark,
        LinearLayout.LayoutParams(dp(Dimens.AVATAR_LARGE), dp(Dimens.AVATAR_LARGE)).apply { marginEnd = dp(Dimens.SPACE_4) },
    )
    val name = column().apply {
        addView(label("주말 드라이버", Dimens.HEADING, bold = true))
        addView(label("이 차에 저장됩니다", Dimens.SMALL, palette.inkMuted))
    }
    addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
}

/** 기록이 없을 때 0을 늘어놓지 않고 무엇이 쌓이는지만 쓴다. */
private fun Context.record(): View {
    val totals = MyDrives.totals(this)
    if (totals.finishedCourses == 0) return label("코스를 끝까지 달리면 여기에 기록이 쌓입니다", Dimens.SMALL, palette.inkMuted)
    val cells = listOf(
        Fact("${totals.finishedCourses}", "끝까지 달린 코스"),
        Fact(kmText(totals.meters), "코스로 달린 거리"),
        Fact("${totals.curves}", "지나온 굽이"),
        Fact("${MyDrives.saved(this).size}", "저장한 코스"),
    ).map { factCell(it, Dimens.VALUE) }
    return grid(cells, columns = 2, rowHeight = dp(Dimens.RECORD_CELL_HEIGHT), gap = Dimens.SPACE_3)
}

/** 켜고 끄는 줄. 움직임 없이 색과 위치만 바뀐다. */
private fun Context.toggleRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View = row().apply {
    var on = initial
    minimumHeight = dp(Dimens.TOUCH_MIN)
    val knob = View(context)
    val pill = FrameLayout(context)
    fun paint() {
        pill.background = rounded(if (on) palette.accent else palette.bgPressed, Dimens.TOGGLE_HEIGHT / 2)
        knob.background = rounded(if (on) palette.onAccent else palette.inkMuted, Dimens.TOGGLE_KNOB / 2)
        val side = if (on) Gravity.END else Gravity.START
        knob.layoutParams = FrameLayout.LayoutParams(dp(Dimens.TOGGLE_KNOB), dp(Dimens.TOGGLE_KNOB), side or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(Dimens.TOGGLE_INSET)
            marginEnd = dp(Dimens.TOGGLE_INSET)
        }
    }
    pill.addView(knob)
    paint()
    addView(label(title, Dimens.BODY), LinearLayout.LayoutParams(0, WRAP, 1f))
    addView(pill, LinearLayout.LayoutParams(dp(Dimens.TOGGLE_WIDTH), dp(Dimens.TOGGLE_HEIGHT)))
    contentDescription = title
    setOnClickListener {
        on = !on
        onChange(on)
        paint()
    }
}
