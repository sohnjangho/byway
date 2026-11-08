package com.byway.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.byway.BuildConfig
import com.byway.core.course.Course
import com.byway.core.course.Weather
import com.byway.service.CourseCatalog
import com.byway.service.DriveBoard
import com.byway.service.DriveService
import com.byway.service.DriveState

/**
 * 상태를 받아 화면을 바꾸기만 한다. 어떤 화면인지는 [screenFor]가 정한다.
 * 창 막대를 끌거나 시스템 주야 모드가 바뀌면 Activity 가 다시 만들어지고 [Nav]를 Bundle 에서 되살린다.
 * 다시 만들어지지 않고 살아 있어도 root 폭과 [onConfigurationChanged]를 보고 지금 화면을 다시 그린다.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var book: CourseBook
    private lateinit var root: FrameLayout
    private var nav = Nav()
    private var pendingStart: String? = null
    private var stale = false
    private var current: Screen? = null
    private var builtWidth = 0
    private var builtPalette: Palette? = null
    private var builtTab = nav.tab
    private var builtCourse: String? = null
    private val render: (DriveState) -> Unit = ::render

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        (savedInstanceState?.getSerializable(NAV) as? Nav)?.let { nav = it }
        book = sharedBook ?: CourseBook(CourseCatalog.all(this)).also { sharedBook = it }
        root = FrameLayout(this)
        root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (widthDp() != builtWidth) view.post { render(DriveBoard.state) }
        }
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(NAV, nav)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        render(DriveBoard.state)
    }

    override fun onStart() {
        super.onStart()
        DriveBoard.listen(render)
    }

    override fun onStop() {
        DriveBoard.stopListening(render)
        super.onStop()
    }

    private fun go(next: Nav) {
        nav = next
        stale = true
        render(DriveBoard.state)
    }

    private fun openTab(tab: Tab) {
        leaveSummary()
        go(nav.copy(tab = tab, about = false, picked = null, atlas = null))
    }

    private fun leaveSummary() {
        if (DriveBoard.state.finished != null) DriveBoard.update { it.copy(finished = null) }
    }

    private fun start(courseId: String) {
        if (checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED) {
            DriveService.start(this, courseId)
        } else {
            pendingStart = courseId
            requestPermissions(arrayOf(LOCATION), LOCATION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == LOCATION_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingStart?.let { DriveService.start(this, it) }
        }
    }

    private fun widthDp(): Int =
        if (root.width > 0) (root.width / resources.displayMetrics.density).toInt() else resources.configuration.screenWidthDp

    private fun render(state: DriveState) {
        if (state.running) nav = nav.copy(about = false)
        val next = screenFor(state, nav)
        val width = widthDp()
        if (next != current || width != builtWidth || palette != builtPalette || stale) {
            val motion = screenMotion(current, next, builtCourse, nav.courseId, width == builtWidth && palette == builtPalette)
            val sign = enterSign(builtTab, nav.tab)
            val reveal = motion != ScreenMotion.NONE && next == Screen.Browse(Tab.HOME) && nav.courseId != builtCourse
            stale = false
            current = next
            builtWidth = width
            builtPalette = palette
            builtTab = nav.tab
            builtCourse = nav.courseId
            window.setBackgroundDrawable(ColorDrawable(palette.bg))
            root.setBackgroundColor(palette.bg)
            place(next, state, BrowseKit(book, width) { open(it) }, motion, sign, reveal)
        }
        (root.getChildAt(0) as? DriveScreen)?.show(state)
    }

    private fun open(course: Course) = go(nav.copy(tab = Tab.HOME, courseId = course.id))

    /** 메뉴가 있는 정차 화면끼리는 메뉴를 두고 내용만 바꾼다. 주행 화면은 움직임 없이 그 자리에서 바꾼다. */
    private fun place(screen: Screen, state: DriveState, kit: BrowseKit, motion: ScreenMotion, sign: Int, reveal: Boolean) {
        val over = screen !is Screen.About
        val content = when (screen) {
            is Screen.Browse -> browse(screen.tab, kit, reveal)
            Screen.About -> aboutScreen(this, kit) { go(nav.copy(about = false)) }
            else -> null
        }
        val frame = root.getChildAt(0) as? RailFrame
        if (content != null && motion != ScreenMotion.NONE && frame != null) {
            frame.swap(content, nav.tab, over, sign)
            return
        }
        root.removeAllViews()
        root.addView(
            when {
                content != null -> RailFrame(this, nav.tab, kit.window, ::openTab, content, over)
                screen is Screen.Summary -> summaryScreen(this, kit, screen.course) { openTab(Tab.HOME) }
                screen == Screen.Drive -> DriveScreen(this, book, requireNotNull(state.course), kit.wide) { DriveService.stop(this) }
                else -> waitingScreen(this)
            },
        )
    }

    private fun browse(tab: Tab, kit: BrowseKit, reveal: Boolean): View = when (tab) {
        Tab.HOME -> homeScreen(
            this,
            kit,
            courseId = nav.courseId,
            weather = demoWeather(),
            stripX = nav.stripX,
            reveal = reveal,
            onPick = { course, x -> go(nav.copy(tab = Tab.HOME, courseId = course.id, stripX = x)) },
            onStart = ::start,
            onBrowse = { openTab(Tab.BROWSE) },
        )
        Tab.BROWSE -> browseScreen(this, kit, nav, { nav = it }, ::go)
        Tab.RANKING -> rankingScreen(this, kit, nav, { nav = it }, ::go)
        Tab.MINE -> mineScreen(
            this,
            kit,
            nav,
            { nav = it },
            onStart = ::start,
            onBrowse = { openTab(Tab.BROWSE) },
            onAbout = { go(nav.copy(about = true)) },
        )
    }

    /** 날씨 입력이 아직 없다. 시연용 debug 빌드에서만 --es weather rain 으로 분위기를 고른다. */
    private fun demoWeather(): Weather = if (!BuildConfig.DEBUG) {
        Weather.UNKNOWN
    } else {
        Weather.entries.firstOrNull { it.name.equals(intent.getStringExtra("weather"), ignoreCase = true) } ?: Weather.UNKNOWN
    }

    private companion object {
        const val LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val LOCATION_REQUEST = 1
        const val NAV = "nav"

        /** 창 크기가 바뀌어 Activity 가 다시 만들어져도 코스 수치를 다시 계산하지 않는다. */
        var sharedBook: CourseBook? = null
    }
}
