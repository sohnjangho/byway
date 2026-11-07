package com.byway.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.byway.core.course.Course
import com.byway.service.CourseCatalog
import com.byway.service.DriveBoard
import com.byway.service.DriveService
import com.byway.service.DriveState

/** 상태를 받아 화면을 바꾸기만 한다. 어떤 화면인지는 [screenFor]가 정한다. */
class MainActivity : AppCompatActivity() {
    private lateinit var courses: List<Course>
    private lateinit var root: FrameLayout
    private var selectedCourseId: String? = null
    private var showAbout = false
    private var current: Screen? = null
    private val render: (DriveState) -> Unit = ::render

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        courses = CourseCatalog.all(this)
        root = FrameLayout(this).apply { setBackgroundColor(Look.BACKGROUND) }
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        DriveBoard.listen(render)
    }

    override fun onStop() {
        DriveBoard.stopListening(render)
        super.onStop()
    }

    private fun open(courseId: String? = null, about: Boolean = false) {
        selectedCourseId = courseId
        showAbout = about
        render(DriveBoard.state)
    }

    private fun start(courseId: String) {
        if (checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED) {
            DriveService.start(this, courseId)
        } else {
            requestPermissions(arrayOf(LOCATION), LOCATION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == LOCATION_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            (current as? Screen.Preview)?.let { DriveService.start(this, it.courseId) }
        }
    }

    private fun render(state: DriveState) {
        if (state.running) {
            selectedCourseId = null
            showAbout = false
        }
        val next = screenFor(state, selectedCourseId, showAbout)
        if (next != current) {
            current = next
            root.removeAllViews()
            root.addView(build(next, state))
        }
        (root.getChildAt(0) as? DriveScreen)?.show(state)
    }

    private fun build(screen: Screen, state: DriveState): View = when (screen) {
        Screen.Home -> homeScreen(this, courses, onCourse = { open(courseId = it.id) }, onAbout = { open(about = true) })
        is Screen.Preview -> previewScreen(
            this,
            courses.first { it.id == screen.courseId },
            onBack = { open() },
            onStart = { start(screen.courseId) },
        )
        Screen.Drive -> DriveScreen(this, state.course) { DriveService.stop(this) }
        is Screen.Summary -> summaryScreen(this, screen.course) { DriveBoard.update { it.copy(finished = null) } }
        Screen.Waiting -> waitingScreen(this)
        Screen.About -> aboutScreen(this, courses) { open() }
    }

    private companion object {
        const val LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val LOCATION_REQUEST = 1
    }
}
