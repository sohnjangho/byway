package com.byway.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 파티 확대 지도의 지도 조각(주행 화면 홈 맞춤 설계 2절). 메모리, 기기 캐시, 네트워크 순서로 찾는다.
 * 받지 못하면 null 을 주고 30초 뒤에 다시 받는다. 그동안 화면은 분위기 색 위에 코스 선만 그린다.
 * 상태(pending, failedAt)는 메인 스레드에서만 만진다.
 */
internal object MapTileCache {
    private const val MEMORY_TILES = 64
    private const val WORKERS = 2
    private const val RETRY_MS = 30_000L
    private const val TIMEOUT_SECONDS = 8L
    private const val AGENT = "byway-app"

    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newFixedThreadPool(WORKERS)
    private val http = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val memory = LruCache<String, Bitmap>(MEMORY_TILES)
    private val pending = mutableSetOf<String>()
    private val failedAt = mutableMapOf<String, Long>()

    /** 있으면 바로 준다. 없으면 받으러 보내고, 끝나면(성공이든 실패든) [onReady]를 메인 스레드에서 부른다. */
    fun get(context: Context, style: MapStyle, night: Boolean, z: Int, x: Int, y: Int, onReady: () -> Unit): Bitmap? {
        val key = "${style.name}/${if (night) "night" else "day"}/$z/$x/$y"
        memory.get(key)?.let { return it }
        if (key in pending) return null
        val failed = failedAt[key]
        if (failed != null && SystemClock.elapsedRealtime() - failed < RETRY_MS) return null
        pending += key
        val file = File(context.applicationContext.cacheDir, "tiles/${style.name.lowercase()}/$z/$x/$y.jpg")
        workers.execute {
            val bitmap = runCatching { read(file, tileUrl(style, z, x, y), style, night) }.getOrNull()
            main.post {
                pending -= key
                if (bitmap == null) {
                    failedAt[key] = SystemClock.elapsedRealtime()
                } else {
                    failedAt -= key
                    memory.put(key, bitmap)
                }
                onReady()
            }
        }
        return null
    }

    /** 기기 캐시에는 받은 그대로 두고, 밤 색은 읽을 때 바꾼다. */
    private fun read(file: File, url: String, style: MapStyle, night: Boolean): Bitmap? {
        if (!file.exists()) download(url, file)
        if (!night) {
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
        }
        val day = BitmapFactory.decodeFile(file.path) ?: return null
        val pixels = IntArray(day.width * day.height)
        day.getPixels(pixels, 0, day.width, 0, 0, day.width, day.height)
        val bg = NightPalette.bg
        for (i in pixels.indices) pixels[i] = if (style == MapStyle.MAP) nightMapPixel(pixels[i], bg) else nightSatPixel(pixels[i])
        return Bitmap.createBitmap(pixels, day.width, day.height, Bitmap.Config.RGB_565).also { day.recycle() }
    }

    private fun download(url: String, file: File) {
        val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
        http.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) error("tile ${response.code}")
            file.parentFile?.mkdirs()
            val part = File(file.path + ".part")
            part.outputStream().use { body.byteStream().copyTo(it) }
            // 다 받은 뒤에만 이름을 바꿔, 끊긴 파일이 캐시에 남지 않게 한다.
            if (!part.renameTo(file)) part.delete()
        }
    }
}
