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

private const val PROFILE_WIDTH = 500
private const val SHELF_HEIGHT = 380
private const val PER_ROW = 3

/** 프로필, 내 기록, 최근 코스, 저장한 코스, 설정. 이 차에 저장된 값만 보여 준다. */
internal fun mineScreen(context: Context, kit: BrowseKit, onBrowse: () -> Unit, onAbout: () -> Unit): View =
    with(context) {
        val profile = profileCard(kit, onAbout)
        val shelves = column().apply {
            addView(shelfTitle("최근 달린 코스"))
            addSpaced(recentShelf(kit), 12)
            addSpaced(shelfTitle("저장한 코스"), Look.GAP)
            addSpaced(savedShelf(kit, onBrowse), 12)
        }
        val edge = dp(kit.edge)
        if (kit.wide) {
            LinearLayout(context).apply {
                setPadding(edge, dp(Look.WIDE_GAP), edge, dp(Look.WIDE_GAP))
                addView(profile, LinearLayout.LayoutParams(dp(PROFILE_WIDTH), MATCH).apply { marginEnd = dp(Look.WIDE_GAP) })
                addView(ScrollView(context).apply { addView(shelves) }, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
        } else {
            val body = column().apply {
                setPadding(edge, dp(Look.GAP), edge, dp(Look.WIDE_GAP))
                addView(profile)
                addSpaced(shelves, Look.GAP)
            }
            ScrollView(context).apply { addView(body) }
        }
    }

private fun Context.shelfTitle(text: String) = label(text, Look.HEADING, bold = true)

private fun Context.courseShelf(kit: BrowseKit, courses: List<Course>): View {
    val cells = courses.map { course -> coverCard(course, null, dp(SHELF_HEIGHT)) { kit.onCourse(course) } }
    val used = if (kit.wide) PROFILE_WIDTH + Look.WIDE_GAP else 0
    return coverRow(cells, dp(kit.cellWidth(if (kit.wide) PER_ROW else 2, used)), dp(SHELF_HEIGHT))
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
        addSpaced(quietButton("코스 고르러 가기", onBrowse), 12)
    }
}

private fun Context.profileCard(kit: BrowseKit, onAbout: () -> Unit): View = column().apply {
    background = rounded(Look.SURFACE)
    setPadding(dp(Look.WIDE_GAP), dp(Look.WIDE_GAP), dp(Look.WIDE_GAP), dp(Look.WIDE_GAP))
    addView(identity())
    addSpaced(record(), Look.GAP)
    if (kit.wide) addView(Space(context), fill())
    addSpaced(toggleRow("차 안 연출", MyDrives.ambienceOn) { MyDrives.ambienceOn = it }, Look.GAP)
    addSpaced(toggleRow("음성 안내", MyDrives.voiceOn) { MyDrives.voiceOn = it }, 12)
    addSpaced(quietButton("앱 정보", onAbout), Look.CARD_GAP)
}

private fun Context.identity(): View = row().apply {
    val mark = label("나", Look.VALUE, Look.ACCENT, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(Look.SURFACE_PRESSED, 52)
    }
    addView(mark, LinearLayout.LayoutParams(dp(104), dp(104)).apply { marginEnd = dp(20) })
    val name = column().apply {
        addView(label("주말 드라이버", Look.HEADING, bold = true))
        addView(label("이 차에 저장됩니다", Look.SMALL, Look.MUTED))
    }
    addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
}

/** 기록이 없을 때 0을 늘어놓지 않고 무엇이 쌓이는지만 쓴다. */
private fun Context.record(): View {
    val totals = MyDrives.totals(this)
    if (totals.finishedCourses == 0) return label("코스를 끝까지 달리면 여기에 기록이 쌓입니다", Look.SMALL, Look.MUTED)
    val cells = listOf(
        Fact("${totals.finishedCourses}", "끝까지 달린 코스"),
        Fact(kmText(totals.meters), "코스로 달린 거리"),
        Fact("${totals.curves}", "지나온 굽이"),
        Fact("${MyDrives.saved(this).size}", "저장한 코스"),
    ).map { factCell(it, Look.TITLE) }
    return grid(cells, columns = 2, rowHeight = dp(100))
}

/** 켜고 끄는 줄. 움직임 없이 색과 위치만 바뀐다. */
private fun Context.toggleRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View = row().apply {
    var on = initial
    minimumHeight = dp(Look.TOUCH_MIN)
    val knob = View(context)
    val pill = FrameLayout(context)
    fun paint() {
        pill.background = rounded(if (on) Look.ACCENT else Look.SURFACE_PRESSED, 24)
        knob.background = rounded(if (on) Look.ON_ACCENT else Look.MUTED, 18)
        val side = if (on) Gravity.END else Gravity.START
        knob.layoutParams = FrameLayout.LayoutParams(dp(36), dp(36), side or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }
    }
    pill.addView(knob)
    paint()
    addView(label(title, Look.BODY), LinearLayout.LayoutParams(0, WRAP, 1f))
    addView(pill, LinearLayout.LayoutParams(dp(84), dp(48)))
    contentDescription = title
    setOnClickListener {
        on = !on
        onChange(on)
        paint()
    }
}
