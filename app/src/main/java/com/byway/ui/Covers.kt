package com.byway.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/** 코스 커버를 앱 자산에서 읽는다. 코스가 많아도 화면이 멈추지 않게 작은 크기로 뒤에서 읽고 한 번 읽은 것은 둔다. */
internal object Covers {
    private const val FOLDER = "covers"
    private const val CACHE_BYTES = 48 * 1024 * 1024

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private var available: Set<String>? = null

    /** 커버가 있으면 [onLoaded]를 메인 스레드에서 부른다. 없으면 부르지 않는다. 화면은 코스 그림을 그대로 둔다. */
    fun load(context: Context, courseId: String, sizePx: Int, onLoaded: (Bitmap) -> Unit) {
        val file = "$courseId.jpg"
        if (file !in files(context)) return
        val sample = sampleFor(sizePx)
        val key = "$courseId/$sample"
        cache[key]?.let { return onLoaded(it) }
        val assets = context.applicationContext.assets
        worker.execute {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = assets.open("$FOLDER/$file").use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@execute
            main.post {
                cache.put(key, bitmap)
                onLoaded(bitmap)
            }
        }
    }

    private fun files(context: Context): Set<String> =
        available ?: context.assets.list(FOLDER).orEmpty().toSet().also { available = it }

    /** 커버는 640px 다. 작은 칸에 그대로 올리면 메모리만 쓴다. */
    private fun sampleFor(sizePx: Int): Int = when {
        sizePx <= 160 -> 4
        sizePx <= 320 -> 2
        else -> 1
    }
}
