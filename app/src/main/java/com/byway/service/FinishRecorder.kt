package com.byway.service

import android.content.Context
import com.byway.core.course.Course
import com.byway.core.course.factsOf
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature

/** 코스를 끝까지 달렸을 때만 내 기록에 쌓는다. 중간에 끝내면 CourseFinished 가 오지 않는다. */
internal class FinishRecorder(private val context: Context) : DriveFeature {
    override val id = "record"
    private var course: Course? = null

    override fun onEvent(event: DriveEvent): List<DriveCommand> {
        if (event is DriveEvent.CourseStarted) course = event.course
        if (event is DriveEvent.CourseFinished) course?.let(::record)
        return emptyList()
    }

    private fun record(course: Course) {
        val facts = factsOf(course)
        MyDrives.recordFinished(context, course.id, facts.meters, facts.curves)
        // 공유 코스의 완주 횟수. 기기 정보는 보내지 않고, 실패하면 버린다.
        if (course.id.startsWith(SHARED_PREFIX)) Share.io.execute { Share.client(context).finished(course.id) }
    }

    private companion object {
        const val SHARED_PREFIX = "s-"
    }
}
