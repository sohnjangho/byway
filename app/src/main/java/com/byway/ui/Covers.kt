package com.byway.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.byway.core.course.Course
import java.util.concurrent.Executors

/**
 * 지도 그림 파일을 고른다. 커버, 장면, 한국 지도가 같은 규칙을 쓴다.
 * 야간에는 도구가 미리 어둡게 만든 그림을 쓰고, 없으면 주간 그림을 쓴다.
 * 고른 지도 종류의 그림이 없으면 위성 그림으로 대신한다. 새 코스는 일반 지도 그림이 늦게 생길 수 있다.
 */
fun coverFile(name: String, night: Boolean, files: Set<String>, style: MapStyle = MapStyle.SATELLITE): String? =
    listOf(style, MapStyle.SATELLITE).distinct().firstNotNullOfOrNull { kind ->
        val dayFile = "$name${kind.suffix}.jpg"
        val nightFile = "$name${kind.suffix}-night.jpg"
        when {
            night && nightFile in files -> nightFile
            dayFile in files -> dayFile
            else -> null
        }
    }

/** 풍경 사진 파일. 출처를 밝힐 수 없는 사진은 쓰지 않는다. */
fun sceneryFile(courseId: String, credit: String, files: Set<String>): String? =
    "$courseId.jpg".takeIf { credit.isNotBlank() && it in files }

/** 코스 커버를 앱 자산에서 읽는다. 코스가 많아도 화면이 멈추지 않게 작은 크기로 뒤에서 읽고 한 번 읽은 것은 둔다. */
internal object Covers {
    private const val FOLDER = "covers"
    private const val SCENERY_FOLDER = "scenery"
    private const val SCENE_FOLDER = "scene"
    private const val ATLAS_FOLDER = "atlas"
    private const val ATLAS_NAME = "korea"
    private const val FRAMES_FOLDER = "frames"
    private const val FRAME_SIZE = 320
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
        val file = coverFile(courseId, context.night, files(context, FOLDER), currentMapStyle()) ?: return
        read(context, FOLDER, file, sizePx, onLoaded)
    }

    fun hasScenery(context: Context, course: Course): Boolean =
        sceneryFile(course.id, course.sceneryCredit, files(context, SCENERY_FOLDER)) != null

    fun loadScenery(context: Context, course: Course, onLoaded: (Bitmap) -> Unit) {
        val file = sceneryFile(course.id, course.sceneryCredit, files(context, SCENERY_FOLDER)) ?: return
        read(context, SCENERY_FOLDER, file, FULL_SIZE, onLoaded)
    }

    fun hasScene(context: Context, courseId: String, night: Boolean): Boolean = sceneFile(context, courseId, night) != null

    fun loadScene(context: Context, courseId: String, night: Boolean, onLoaded: (Bitmap) -> Unit) {
        val file = sceneFile(context, courseId, night) ?: return
        read(context, SCENE_FOLDER, file, FULL_SIZE, onLoaded)
    }

    private fun sceneFile(context: Context, courseId: String, night: Boolean) =
        coverFile(courseId, night, files(context, SCENE_FOLDER), currentMapStyle())

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

    /** 훑어보기 사진. tools/make-frames.py 가 코스마다 00.jpg 부터 차례로 만든다. */
    fun loadFrame(context: Context, courseId: String, index: Int, onLoaded: (Bitmap) -> Unit) {
        val folder = "$FRAMES_FOLDER/$courseId"
        val file = "%02d.jpg".format(index)
        if (file in files(context, folder)) read(context, folder, file, FRAME_SIZE, onLoaded)
    }

    fun loadAtlas(context: Context, night: Boolean, onLoaded: (Bitmap) -> Unit) {
        val file = coverFile(ATLAS_NAME, night, files(context, ATLAS_FOLDER), currentMapStyle()) ?: return
        read(context, ATLAS_FOLDER, file, FULL_SIZE, onLoaded)
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
