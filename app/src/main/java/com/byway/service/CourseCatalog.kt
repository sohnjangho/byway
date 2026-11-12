package com.byway.service

import android.content.Context
import com.byway.core.course.Course
import com.byway.core.course.parseCourse

/** 앱에 들어 있는 코스를 읽는다. 사용자 코스가 생기면 이곳에 경로를 더한다. */
object CourseCatalog {
    private const val FOLDER = "courses"
    private var cached: List<Course>? = null

    fun all(context: Context): List<Course> = cached ?: load(context).also { cached = it }

    fun find(context: Context, id: String): Course = all(context).first { it.id == id }

    private fun load(context: Context): List<Course> =
        context.assets.list(FOLDER).orEmpty().sorted().map { name ->
            context.assets.open("$FOLDER/$name").bufferedReader().use { parseCourse(it.readText()) }
        }
}
