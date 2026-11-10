package com.byway.service

import android.content.Context
import com.byway.core.course.Course
import com.byway.core.course.parseCourse
import com.byway.core.course.writeCourse
import com.byway.core.share.SharedCourse
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 코스가 들어오는 세 경로를 합친다. 앱 자산 > 내가 만든 코스 > 코드로 받은 코스 > 서버 목록 순서로 id 가 겹치면 앞의 것을 남긴다.
 * 주행은 언제나 이렇게 기기 안에 있는 파일로 한다(공유 설계 6-4).
 */
fun mergeCourses(assets: List<Course>, mine: List<Course>, received: List<Course>, shared: List<Course>): List<Course> =
    (assets + mine + received + shared).distinctBy { it.id }

/** 앱 내부 저장소의 코스 파일. 폴더 하나를 받아 JVM 시험에서도 돈다. */
class CourseFiles(private val root: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mineDir get() = File(root, "mine").apply { mkdirs() }
    private val receivedDir get() = File(root, "received").apply { mkdirs() }
    private val sharedDir get() = File(root, "shared").apply { mkdirs() }

    fun mine(): List<Course> = read(mineDir) { parseCourse(it) }

    fun received(): List<SharedCourse> = read(receivedDir) { json.decodeFromString(SharedCourse.serializer(), it) }

    /** 서버 목록. 내가 올린 것도 들어 있다. */
    fun shared(): List<SharedCourse> = read(sharedDir) { json.decodeFromString(SharedCourse.serializer(), it) }

    fun saveMine(course: Course) = File(mineDir, "${course.id}.json").writeText(writeCourse(course))

    fun deleteMine(id: String) {
        File(mineDir, "$id.json").delete()
    }

    fun saveReceived(course: SharedCourse) = File(receivedDir, "${course.id}.json").writeText(json.encodeToString(SharedCourse.serializer(), course))

    fun deleteReceived(id: String) {
        File(receivedDir, "$id.json").delete()
    }

    /** 서버 목록으로 바꾼다. 목록에서 빠진 코스(지워졌거나 숨겨진 것)는 기기에서도 지운다. */
    fun replaceShared(list: List<SharedCourse>) {
        val keep = list.map { "${it.id}.json" }.toSet()
        sharedDir.listFiles().orEmpty().filter { it.name !in keep }.forEach { it.delete() }
        list.forEach { File(sharedDir, "${it.id}.json").writeText(json.encodeToString(SharedCourse.serializer(), it)) }
    }

    fun clearAll() {
        root.deleteRecursively()
    }

    /** 화면과 주행이 쓰는 전체 코스. 내가 올린 공유본은 내 코스와 겹치므로 뺀다. */
    fun courses(assets: List<Course>): List<Course> = mergeCourses(
        assets,
        mine(),
        received().mapNotNull { runCatching { parseCourse(it.body) }.getOrNull() },
        shared().filterNot { it.mine }.mapNotNull { runCatching { parseCourse(it.body) }.getOrNull() },
    )

    /** 공유 코스의 서버 정보(완주 횟수, 범위, 코드). 받은 코스가 목록보다 앞이다. */
    fun info(id: String): SharedCourse? = received().firstOrNull { it.id == id } ?: shared().firstOrNull { it.id == id }

    private fun <T> read(dir: File, parse: (String) -> T): List<T> =
        dir.listFiles().orEmpty().filter { it.extension == "json" }.sortedBy { it.name }.mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
}

/** 앱 전체가 쓰는 코스 파일. 내가 만든 코스와 올린 공유본의 짝은 설정 파일에 둔다. */
object CourseStore {
    private const val PREFS = "course_store"
    private const val UPLOAD = "upload:"

    fun files(context: Context) = CourseFiles(File(context.filesDir, "courses"))

    /** 내 코스 [localId]를 서버에 올린 id. 올리지 않았으면 null. */
    fun uploadOf(context: Context, localId: String): String? = prefs(context).getString(UPLOAD + localId, null)

    fun setUpload(context: Context, localId: String, sharedId: String?) {
        prefs(context).edit().apply { if (sharedId == null) remove(UPLOAD + localId) else putString(UPLOAD + localId, sharedId) }.apply()
    }

    fun clearAll(context: Context) {
        files(context).clearAll()
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
