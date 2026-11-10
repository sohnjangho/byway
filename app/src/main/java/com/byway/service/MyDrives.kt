package com.byway.service

import android.content.Context
import android.content.SharedPreferences

/** 이 차에 저장되는 내 기록과 설정. 서버가 생기기 전까지 계정 없이 쓴다. */
data class DriveTotals(val finishedCourses: Int = 0, val meters: Double = 0.0, val curves: Int = 0)

/**
 * 달리다 만 코스. 시동을 끄거나 앱이 꺼져도 남아, 다시 켰을 때 이어갈지 묻는다.
 * [progress]는 코스 위의 지점 번호이고 [cabin]은 연출이 손대기 전의 차 안 값이다(platform 의 CabinMemory 가 쓴 글자).
 */
data class SavedRun(val courseId: String, val progress: Int, val cabin: String = "")

object MyDrives {
    private const val FILE = "my_drives"
    private const val FINISHED = "finished_courses"
    private const val METERS = "meters_bits"
    private const val CURVES = "curves"
    private const val RECENT = "recent"
    private const val SAVED = "saved"
    private const val AMBIENCE = "ambience_on"
    private const val VOICE = "voice_on"
    private const val MUSIC = "music_on"
    private const val MOOD = "mood_on"
    private const val DRIVE_LAYOUT = "drive_layout"
    private const val MAP_STYLE = "map_style"
    private const val RUN_COURSE = "run_course"
    private const val RUN_PROGRESS = "run_progress"
    private const val RUN_CABIN = "run_cabin"
    private const val DEFAULT_MAP_STYLE = "map"
    private const val DEFAULT_DRIVE_LAYOUT = "split"
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

    fun savedRun(context: Context): SavedRun? {
        val stored = prefs(context)
        val courseId = stored.getString(RUN_COURSE, null) ?: return null
        return SavedRun(courseId, stored.getInt(RUN_PROGRESS, 0), stored.getString(RUN_CABIN, "").orEmpty())
    }

    fun saveRun(context: Context, run: SavedRun) {
        prefs(context).edit().putString(RUN_COURSE, run.courseId).putInt(RUN_PROGRESS, run.progress).putString(RUN_CABIN, run.cabin).apply()
    }

    fun clearRun(context: Context) {
        prefs(context).edit().remove(RUN_COURSE).remove(RUN_PROGRESS).remove(RUN_CABIN).apply()
    }

    /** 달린 기록, 저장한 코스, 누계를 지운다. 설정은 남긴다. */
    fun clearRecords(context: Context) {
        prefs(context).edit().remove(FINISHED).remove(METERS).remove(CURVES).remove(RECENT).remove(SAVED).apply()
    }

    /** 앱 시작 때 한 번 부른다. 아래 설정은 Context 없이 읽으므로 저장된 값을 여기서 되돌린다. */
    fun restore(context: Context) {
        val stored = prefs(context)
        settings = stored
        ambienceOn = stored.getBoolean(AMBIENCE, true)
        voiceOn = stored.getBoolean(VOICE, true)
        musicOn = stored.getBoolean(MUSIC, true)
        moodOn = stored.getBoolean(MOOD, true)
        mapStyle = stored.getString(MAP_STYLE, DEFAULT_MAP_STYLE) ?: DEFAULT_MAP_STYLE
        driveLayout = stored.getString(DRIVE_LAYOUT, DEFAULT_DRIVE_LAYOUT) ?: DEFAULT_DRIVE_LAYOUT
    }

    /** 지도 그림의 종류. "map" 또는 "satellite". 화면이 읽을 때 뜻을 정한다. */
    var mapStyle: String = DEFAULT_MAP_STYLE
        set(value) {
            field = value
            settings?.edit()?.putString(MAP_STYLE, value)?.apply()
        }

    /** 달릴 때의 화면 배치. "split"(3분할) 또는 "navi"(내비 2/3). 화면이 읽을 때 뜻을 정한다. */
    var driveLayout: String = DEFAULT_DRIVE_LAYOUT
        set(value) {
            field = value
            settings?.edit()?.putString(DRIVE_LAYOUT, value)?.apply()
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

    var moodOn: Boolean = true
        set(value) {
            field = value
            settings?.edit()?.putBoolean(MOOD, value)?.apply()
        }

    var musicOn: Boolean = true
        set(value) {
            field = value
            settings?.edit()?.putBoolean(MUSIC, value)?.apply()
        }
}
