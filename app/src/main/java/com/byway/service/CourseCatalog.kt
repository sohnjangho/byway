package com.byway.service

import android.content.Context
import com.byway.core.course.Course
import com.byway.core.course.parseCourse

/** 앱에 들어 있는 코스와 내가 만든 코스, 받은 코스, 서버 목록을 합쳐 읽는다([CourseFiles.courses]). */
object CourseCatalog {
    private const val FOLDER = "courses"
    private var assets: List<Course>? = null
    private var cached: List<Course>? = null

    /** 코스가 바뀔 때마다 오른다. 화면은 이 값이 바뀌면 코스 책을 다시 만든다. */
    var version = 0
        private set

    fun all(context: Context): List<Course> = cached ?: CourseStore.files(context).courses(assets(context)).also { cached = it }

    fun find(context: Context, id: String): Course = all(context).first { it.id == id }

    /** 내 코스를 저장하거나 지웠을 때, 서버 목록을 새로 받았을 때 부른다. */
    fun invalidate() {
        cached = null
        version++
    }

    private fun assets(context: Context): List<Course> = assets ?: context.assets.list(FOLDER).orEmpty().sorted().map { name ->
        context.assets.open("$FOLDER/$name").bufferedReader().use { parseCourse(it.readText()) }
    }.also { assets = it }
}
