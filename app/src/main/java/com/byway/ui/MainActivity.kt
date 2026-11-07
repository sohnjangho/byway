package com.byway.ui

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.byway.core.course.Course
import com.byway.core.course.parseCourse
import com.byway.core.engine.DriveTuning
import com.byway.core.engine.planLegs
import com.byway.core.engine.planStops
import com.byway.service.DriveBoard
import com.byway.service.DriveService
import com.byway.service.DriveState

class MainActivity : AppCompatActivity() {
    private lateinit var course: Course
    private lateinit var line: TextView
    private lateinit var spoken: TextView
    private lateinit var cabin: TextView
    private lateinit var action: Button
    private lateinit var map: CoursePathView
    private val render: (DriveState) -> Unit = ::render

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        course = parseCourse(assets.open(ASSET).bufferedReader().use { it.readText() })
        val stops = planStops(course, DriveTuning().simplifyToleranceMeters)
        val legs = planLegs(stops)
        val kilometers = course.path.zipWithNext { a, b -> a.distanceTo(b) }.sum() / 1_000

        map = CoursePathView(this).apply { show(course.path, stops) }
        line = text("", 34f, INK).apply { setPadding(0, 40, 0, 8) }
        spoken = text("", 24f, ACCENT)
        cabin = text("", 24f, MUTED).apply { setPadding(0, 8, 0, 32) }
        action = Button(this).apply {
            textSize = 26f
            setTextColor(BACKGROUND)
            setBackgroundColor(ACCENT)
            setPadding(48, 28, 48, 28)
            setOnClickListener { toggle() }
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(72, 48, 48, 48)
            addView(text("에움길", 56f, ACCENT))
            addView(text("최단 경로가 아니라, 그 길을 달리게 합니다", 24f, MUTED))
            addView(text(course.name, 40f, INK).apply { setPadding(0, 56, 0, 8) })
            addView(text(course.sections.joinToString(" → ") { it.name }, 24f, MUTED))
            addView(text("%.1fkm".format(kilometers), 24f, MUTED))
            addView(
                text("코스 좌표 ${course.path.size}개 → 내비 지점 ${stops.size}개 → ${legs.size}번에 나눠 안내", 24f, INK)
                    .apply { setPadding(0, 24, 0, 0) },
            )
            addView(line)
            addView(spoken)
            addView(cabin)
            addView(action)
        }
        setContentView(
            LinearLayout(this).apply {
                setBackgroundColor(BACKGROUND)
                addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                addView(map, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            },
        )
    }

    override fun onStart() {
        super.onStart()
        DriveBoard.listen(render)
    }

    override fun onStop() {
        DriveBoard.stopListening(render)
        super.onStop()
    }

    private fun toggle() {
        when {
            DriveBoard.state.running -> DriveService.stop(this)
            checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED -> DriveService.start(this, ASSET)
            else -> requestPermissions(arrayOf(LOCATION), LOCATION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == LOCATION_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            DriveService.start(this, ASSET)
        }
    }

    private fun render(state: DriveState) {
        line.text = if (state.running) state.line else ""
        spoken.text = if (state.spoken.isEmpty()) "" else "음성: ${state.spoken}"
        cabin.text = if (state.cabin.isEmpty()) "" else "차 안: ${state.cabin}"
        action.text = if (state.running) "코스 끝내기" else "내비에 코스 입히기"
        map.moveTo(state.position)
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    private companion object {
        const val ASSET = "courses/paldang-sunset.json"
        const val LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val LOCATION_REQUEST = 1
        val BACKGROUND = Color.parseColor("#14181D")
        val INK = Color.parseColor("#F5F1E8")
        val MUTED = Color.parseColor("#9AA3AD")
        val ACCENT = Color.parseColor("#E8A15C")
    }
}
