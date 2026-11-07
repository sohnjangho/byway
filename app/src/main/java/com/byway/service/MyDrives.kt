package com.byway.service

import android.content.Context

/** 이 차에 저장되는 내 기록과 설정. 서버가 생기기 전까지 계정 없이 쓴다. */
data class DriveTotals(val finishedCourses: Int = 0, val meters: Double = 0.0, val curves: Int = 0)

object MyDrives {
    private var saved = emptySet<String>()
    private var recent = emptyList<String>()
    private var totals = DriveTotals()

    fun totals(context: Context): DriveTotals = totals

    /** 끝까지 달린 코스 id. 최근 것이 앞에 온다. */
    fun recent(context: Context): List<String> = recent

    fun saved(context: Context): Set<String> = saved

    fun toggleSaved(context: Context, courseId: String) {
        saved = if (courseId in saved) saved - courseId else saved + courseId
    }

    fun recordFinished(context: Context, courseId: String, meters: Double, curves: Int) {
        recent = listOf(courseId) + recent.filter { it != courseId }
        totals = DriveTotals(totals.finishedCourses + 1, totals.meters + meters, totals.curves + curves)
    }

    var ambienceOn: Boolean = true
    var voiceOn: Boolean = true
}
