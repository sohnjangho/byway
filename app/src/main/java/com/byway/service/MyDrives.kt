package com.byway.service

import android.content.Context
import android.content.SharedPreferences

/** 이 차에 저장되는 내 기록과 설정. 서버가 생기기 전까지 계정 없이 쓴다. */
data class DriveTotals(val finishedCourses: Int = 0, val meters: Double = 0.0, val curves: Int = 0)

object MyDrives {
    private const val FILE = "my_drives"
    private const val FINISHED = "finished_courses"
    private const val METERS = "meters_bits"
    private const val CURVES = "curves"
    private const val RECENT = "recent"
    private const val SAVED = "saved"
    private const val AMBIENCE = "ambience_on"
    private const val VOICE = "voice_on"
    private const val RECENT_LIMIT = 10
    private const val SEPARATOR = "\n"

    private var settings: SharedPreferences? = null

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun totals(context: Context): DriveTotals {
        val stored = prefs(context)
        return DriveTotals(
            stored.getInt(FINISHED, 0),
            Double.fromBits(stored.getLong(METERS, 0.0.toRawBits())),
            stored.getInt(CURVES, 0),
        )
    }

    /** 끝까지 달린 코스 id. 최근 것이 앞에 온다. */
    fun recent(context: Context): List<String> =
        prefs(context).getString(RECENT, "").orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }

    fun saved(context: Context): Set<String> = prefs(context).getStringSet(SAVED, emptySet()).orEmpty().toSet()

    fun toggleSaved(context: Context, courseId: String) {
        val saved = saved(context)
        prefs(context).edit().putStringSet(SAVED, if (courseId in saved) saved - courseId else saved + courseId).apply()
    }

    fun recordFinished(context: Context, courseId: String, meters: Double, curves: Int) {
        val totals = totals(context)
        val recent = (listOf(courseId) + recent(context).filter { it != courseId }).take(RECENT_LIMIT)
        prefs(context).edit()
            .putInt(FINISHED, totals.finishedCourses + 1)
            .putLong(METERS, (totals.meters + meters).toRawBits())
            .putInt(CURVES, totals.curves + curves)
            .putString(RECENT, recent.joinToString(SEPARATOR))
            .apply()
    }

    /** 앱 시작 때 한 번 부른다. 아래 두 설정은 Context 없이 읽으므로 저장된 값을 여기서 되돌린다. */
    fun restore(context: Context) {
        val stored = prefs(context)
        settings = stored
        ambienceOn = stored.getBoolean(AMBIENCE, true)
        voiceOn = stored.getBoolean(VOICE, true)
    }

    var ambienceOn: Boolean = true
        set(value) {
            field = value
            settings?.edit()?.putBoolean(AMBIENCE, value)?.apply()
        }

    var voiceOn: Boolean = true
        set(value) {
            field = value
            settings?.edit()?.putBoolean(VOICE, value)?.apply()
        }
}
