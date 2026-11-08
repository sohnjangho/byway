package com.byway.service

import android.content.Context
import android.util.Log
import com.byway.core.course.CourseScene
import com.byway.core.course.SceneIndex
import com.byway.core.course.parseSceneIndex

/** 앱에 들어 있는 장면 자료를 읽는다. 읽지 못하면 빈 자료를 주고 화면은 코스 그림으로 대신한다. */
object SceneCatalog {
    private const val FILE = "scene/index.json"
    private var cached: SceneIndex? = null

    fun index(context: Context): SceneIndex = cached ?: load(context).also { cached = it }

    fun of(context: Context, courseId: String): CourseScene? = index(context).courses[courseId]

    private fun load(context: Context): SceneIndex =
        runCatching { context.assets.open(FILE).bufferedReader().use { parseSceneIndex(it.readText()) } }
            .onFailure { Log.w("DRIVE", "scene index unavailable", it) }
            .getOrDefault(SceneIndex.EMPTY)
}
