package com.byway.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.byway.core.course.Course
import java.util.concurrent.Executors

/** 야간에는 사진만 어둡게 만든 커버를 쓴다. 코스 선이 함께 어두워지므로 앱에서 필터를 걸지 않는다. 없으면 주간 커버를 쓴다. */
fun coverFile(courseId: String, night: Boolean, files: Set<String>): String? {
    val dayFile = "$courseId.jpg"
    val nightFile = "$courseId-night.jpg"
    return when {
        night && nightFile in files -> nightFile
        dayFile in files -> dayFile
        else -> null
    }
}

/** 설명 뒤에 까는 흐린 배경 파일. tools/make-covers.py 가 만든다. */
fun softFile(courseId: String, files: Set<String>): String? = "$courseId-soft.jpg".takeIf { it in files }

/** 풍경 사진 파일. 출처를 밝힐 수 없는 사진은 쓰지 않는다. */
fun sceneryFile(courseId: String, credit: String, files: Set<String>): String? =
    "$courseId.jpg".takeIf { credit.isNotBlank() && it in files }

/** 코스 커버를 앱 자산에서 읽는다. 코스가 많아도 화면이 멈추지 않게 작은 크기로 뒤에서 읽고 한 번 읽은 것은 둔다. */
internal object Covers {
    private const val FOLDER = "covers"
    private const val SCENERY_FOLDER = "scenery"
    private const val SCENE_FOLDER = "scene"
    private const val ATLAS_FOLDER = "atlas"
    private const val FULL_SIZE = 1024
    private const val CACHE_BYTES = 48 * 1024 * 1024

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val available = mutableMapOf<String, Set<String>>()
    private val waiters = mutableListOf<Runnable>()
    private var reading = 0

    /** 커버가 있으면 [onLoaded]를 메인 스레드에서 부른다. 없으면 부르지 않는다. 화면은 코스 그림을 그대로 둔다. */
    fun load(context: Context, courseId: String, sizePx: Int, onLoaded: (Bitmap) -> Unit) {
        val file = coverFile(courseId, context.night, files(context, FOLDER)) ?: return
        read(context, FOLDER, file, sizePx, onLoaded)
    }

    fun loadSoft(context: Context, courseId: String, onLoaded: (Bitmap) -> Unit) {
        val file = softFile(courseId, files(context, FOLDER)) ?: return
        read(context, FOLDER, file, FULL_SIZE, onLoaded)
    }

    fun hasScenery(context: Context, course: Course): Boolean =
        sceneryFile(course.id, course.sceneryCredit, files(context, SCENERY_FOLDER)) != null

    fun loadScenery(context: Context, course: Course, onLoaded: (Bitmap) -> Unit) {
        val file = sceneryFile(course.id, course.sceneryCredit, files(context, SCENERY_FOLDER)) ?: return
        read(context, SCENERY_FOLDER, file, FULL_SIZE, onLoaded)
    }

    fun hasScene(context: Context, courseId: String, night: Boolean): Boolean =
        sceneFile(courseId, night) in files(context, SCENE_FOLDER)

    fun loadScene(context: Context, courseId: String, night: Boolean, onLoaded: (Bitmap) -> Unit) {
        if (hasScene(context, courseId, night)) read(context, SCENE_FOLDER, sceneFile(courseId, night), FULL_SIZE, onLoaded)
    }

    private fun sceneFile(courseId: String, night: Boolean) = if (night) "$courseId-night.jpg" else "$courseId.jpg"

    /** 지금 읽는 그림이 다 끝나면, 늦어도 [limitMs] 뒤에는 [action]을 부른다. 메인 스레드에서만 쓴다. */
    fun afterReads(limitMs: Long, action: () -> Unit) {
        if (reading == 0) return action()
        val waiter = object : Runnable {
            override fun run() {
                if (waiters.remove(this)) action()
            }
        }
        waiters += waiter
        main.postDelayed(waiter, limitMs)
    }

    private fun finishRead() {
        reading--
        if (reading > 0) return
        waiters.toList().forEach {
            main.removeCallbacks(it)
            it.run()
        }
    }

    fun loadAtlas(context: Context, night: Boolean, onLoaded: (Bitmap) -> Unit) {
        val file = if (night) "korea-night.jpg" else "korea.jpg"
        if (file in files(context, ATLAS_FOLDER)) read(context, ATLAS_FOLDER, file, FULL_SIZE, onLoaded)
    }

    private fun read(context: Context, folder: String, file: String, sizePx: Int, onLoaded: (Bitmap) -> Unit) {
        val sample = sampleFor(sizePx)
        val key = "$folder/$file/$sample"
        cache[key]?.let { return onLoaded(it) }
        val assets = context.applicationContext.assets
        reading++
        worker.execute {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = runCatching { assets.open("$folder/$file").use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
            main.post {
                bitmap?.let {
                    cache.put(key, it)
                    onLoaded(it)
                }
                finishRead()
            }
        }
    }

    private fun files(context: Context, folder: String): Set<String> =
        available.getOrPut(folder) { context.assets.list(folder).orEmpty().toSet() }

    /** 커버는 640px 다. 작은 칸에 그대로 올리면 메모리만 쓴다. */
    private fun sampleFor(sizePx: Int): Int = when {
        sizePx <= 160 -> 4
        sizePx <= 320 -> 2
        else -> 1
    }
}
