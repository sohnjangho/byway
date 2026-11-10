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
import com.byway.service.ShareSync
import com.byway.ui.share.shareScreen
import com.byway.ui.party.partyScreen
import com.byway.ui.record.RecordingView
import com.byway.ui.record.recordConsentScreen
import com.byway.ui.record.saveRecordScreen
import com.byway.service.MyDrives
import com.byway.service.SavedRun

/**
 * 상태를 받아 화면을 바꾸기만 한다. 어떤 화면인지는 [screenFor]가 정한다.
 * 창 막대를 끌거나 시스템 주야 모드가 바뀌면 Activity 가 다시 만들어지고 [Nav]를 Bundle 에서 되살린다.
 * 다시 만들어지지 않고 살아 있어도 root 폭과 [onConfigurationChanged]를 보고 지금 화면을 다시 그린다.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var book: CourseBook
    private lateinit var root: FrameLayout
    private var nav = Nav()
    private var pendingStart: Pair<String, Int>? = null

    /** 이어가지 않기로 한 뒤에는 저장이 지워지는 동안에도 다시 묻지 않는다. */
    private var declined = false
    private var stale = false
    private var pendingRecord = false
    private var current: Screen? = null
    private var builtWidth = 0
    private var builtPalette: Palette? = null
    private var builtPosition = railPosition(nav)
    private var builtCourse: String? = null
    private val render: (DriveState) -> Unit = ::render

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        (savedInstanceState?.getSerializable(NAV) as? Nav)?.let { nav = it }
        book = currentBook()
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
        go(nav.toTab(tab))
    }

    private fun leaveSummary() {
        if (DriveBoard.state.finished != null) DriveBoard.update { it.copy(finished = null) }
    }

    /** 기록 동의 버튼을 누른 뒤에만 부른다. 위치 권한이 없으면 먼저 묻는다. */
    private fun record() {
        if (checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED) {
            DriveService.recordStart(this)
        } else {
            pendingRecord = true
            requestPermissions(arrayOf(LOCATION), LOCATION_REQUEST)
        }
    }

    private fun start(courseId: String, resumeFrom: Int = 0) {
        if (checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED) {
            DriveService.start(this, courseId, resumeFrom)
        } else {
            pendingStart = courseId to resumeFrom
            requestPermissions(arrayOf(LOCATION), LOCATION_REQUEST)
        }
    }

    /** 달리다 만 코스. 코스 목록에 없는 것은 묻지 않는다. */
    private fun savedRun(): SavedRun? =
        MyDrives.savedRun(this)?.takeIf { run -> !declined && book.courses.any { it.id == run.courseId } }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == LOCATION_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingStart?.let { (courseId, resumeFrom) -> DriveService.start(this, courseId, resumeFrom) }
            if (pendingRecord) DriveService.recordStart(this)
        }
    }

    private fun widthDp(): Int =
        if (root.width > 0) (root.width / resources.displayMetrics.density).toInt() else resources.configuration.screenWidthDp

    private fun render(state: DriveState) {
        if (state.running) nav = nav.whileDriving()
        // 내 코스를 저장했거나 서버 목록을 받으면 코스 책을 다시 만든다.
        if (bookVersion != CourseCatalog.version) {
            book = currentBook()
            stale = true
        }
        val saved = savedRun()
        val next = screenFor(state, nav, resumable = saved != null)
        // 정차 화면을 열 때 공유 코스 목록을 새로 받는다. 바뀌었을 때만 화면을 다시 그린다.
        if (next is Screen.Browse) ShareSync.refresh(this) { render(DriveBoard.state) }
        val width = widthDp()
        if (next != current || width != builtWidth || palette != builtPalette || stale) {
            val motion = screenMotion(current, next, builtCourse, nav.courseId, width == builtWidth && palette == builtPalette)
            val sign = railPosition(nav).compareTo(builtPosition)
            val reveal = motion != ScreenMotion.NONE && next == Screen.Browse(Tab.HOME) && nav.courseId != builtCourse
            stale = false
            current = next
            builtWidth = width
            builtPalette = palette
            builtPosition = railPosition(nav)
            builtCourse = nav.courseId
            window.setBackgroundDrawable(ColorDrawable(palette.bg))
            root.setBackgroundColor(palette.bg)
            place(next, state, BrowseKit(book, width) { open(it) }, motion, sign, reveal, saved)
        }
        (root.getChildAt(0) as? DriveScreen)?.show(state)
        (root.getChildAt(0) as? RecordingView)?.show(state)
    }

    private fun open(course: Course) = go(nav.copy(tab = Tab.HOME, courseId = course.id))

    /** 메뉴가 있는 정차 화면끼리는 메뉴를 두고 내용만 바꾼다. 주행 화면은 움직임 없이 그 자리에서 바꾼다. */
    private fun place(screen: Screen, state: DriveState, kit: BrowseKit, motion: ScreenMotion, sign: Int, reveal: Boolean, saved: SavedRun?) {
        val over = screen !is Screen.About
        val content = when (screen) {
            is Screen.Browse -> browse(screen.tab, kit, reveal)
            Screen.About -> aboutScreen(this, kit) { go(nav.copy(about = false)) }
            Screen.Settings -> settingsScreen(this, kit, onAbout = { go(nav.copy(about = true)) }, onChanged = { go(nav) })
            else -> null
        }
        val frame = root.getChildAt(0) as? RailFrame
        if (content != null && motion != ScreenMotion.NONE && frame != null) {
            frame.swap(content, markedTab(nav), over, sign)
            return
        }
        root.removeAllViews()
        root.addView(
            when {
                content != null -> RailFrame(this, markedTab(nav), kit.window, ::openTab, { go(nav.copy(settings = true, about = false)) }, content, over)
                screen is Screen.Summary -> summaryScreen(this, kit, screen.course) { openTab(Tab.HOME) }
                screen == Screen.Drive -> DriveScreen(
                    this, book, requireNotNull(state.course), kit.window,
                    onAnswer = { DriveService.answer(this, yes = true) },
                    onEnd = { DriveService.stop(this) },
                )
                screen == Screen.Resume && saved != null -> resumeScreen(
                    this, kit, book.courses.first { it.id == saved.courseId }, saved.progress,
                    onResume = { start(saved.courseId, saved.progress) },
                    onDiscard = {
                        declined = true
                        DriveService.discard(this)
                        stale = true
                        render(DriveBoard.state)
                    },
                )
                screen == Screen.RecordConsent -> recordConsentScreen(
                    this, kit,
                    onAgree = {
                        go(nav.copy(recordConsent = false))
                        record()
                    },
                    onCancel = { go(nav.copy(recordConsent = false)) },
                )
                screen is Screen.Share && book.courses.any { it.id == screen.courseId } -> shareScreen(
                    this, kit, book.find(screen.courseId),
                    onDone = { go(nav.copy(share = null)) },
                    onCancel = { go(nav.copy(share = null)) },
                )
                screen == Screen.Party -> partyScreen(this, kit, onStart = ::start, onClose = { go(nav.copy(party = false)) })
                screen == Screen.Recording -> RecordingView(this, kit.window) { DriveService.recordStop(this) }.also { it.show(state) }
                screen == Screen.SaveRecord -> saveRecordScreen(
                    this, kit, state.recorded.orEmpty(),
                    onSaved = {
                        DriveBoard.update { it.copy(recorded = null) }
                        go(nav.toTab(Tab.MINE))
                    },
                    onDiscard = { DriveBoard.update { it.copy(recorded = null) } },
                )
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
            onChanged = { go(nav) },
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
            onRecord = { go(nav.copy(recordConsent = true)) },
            onShare = { id -> go(nav.copy(share = id)) },
            onChanged = { go(nav) },
            onParty = { go(nav.copy(party = true)) },
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
        var bookVersion = -1
    }

    private fun currentBook(): CourseBook = sharedBook?.takeIf { bookVersion == CourseCatalog.version }
        ?: CourseBook(CourseCatalog.all(this)).also {
            sharedBook = it
            bookVersion = CourseCatalog.version
        }
}
