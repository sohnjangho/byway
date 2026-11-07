package com.byway.platform

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byway.service.CourseCatalog
import com.byway.service.MapPane
import com.byway.service.MapPanes
import com.byway.service.MapStyle
import com.byway.ui.MainActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** 이 차량 OS에서 지도가 실제로 그려지는지 본다. 코스 선은 타일과 달리 네트워크 없이도 나와야 한다. */
@RunWith(AndroidJUnit4::class)
class MapPaneTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val course = CourseCatalog.find(app, "paldang-sunset")
    private lateinit var activity: Activity
    private lateinit var pane: MapPane

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Before
    fun setUp() {
        activity = instrumentation.startActivitySync(
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        onMain { pane = requireNotNull(MapPanes.create(activity)) }
    }

    @After
    fun tearDown() {
        onMain {
            pane.release()
            detach()
        }
        activity.finish()
    }

    private fun attach() = activity.addContentView(pane.view, ViewGroup.LayoutParams(WIDTH, HEIGHT))

    private fun detach() {
        (pane.view.parent as? ViewGroup)?.removeView(pane.view)
    }

    private fun texture(view: View): TextureView? = when (view) {
        is TextureView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { texture(view.getChildAt(it)) }
        else -> null
    }

    private fun screenshot(): Bitmap? {
        var shot: Bitmap? = null
        onMain { shot = texture(pane.view)?.bitmap }
        return shot
    }

    private fun Bitmap.count(color: String): Int {
        val wanted = Color.parseColor(color)
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        return pixels.count {
            abs(Color.red(it) - Color.red(wanted)) <= 2 &&
                abs(Color.green(it) - Color.green(wanted)) <= 2 &&
                abs(Color.blue(it) - Color.blue(wanted)) <= 2
        }
    }

    private fun awaitLine(colors: List<String> = MOODS) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var counts = emptyList<Int>()
        while (System.currentTimeMillis() < deadline) {
            val shot = screenshot()
            counts = colors.map { shot?.count(it) ?: 0 }
            if (counts.all { it >= MIN_PIXELS }) return
            Thread.sleep(250)
        }
        assertTrue("코스 선 픽셀이 부족하다: $counts", false)
    }

    @Test
    fun mapIsAvailableOnThisCar() {
        assertNotNull(pane)
    }

    @Test
    fun drawsEverySectionOfTheCourseOnSatellite() {
        onMain {
            attach()
            pane.show(course, MapStyle.SATELLITE)
        }

        awaitLine()
    }

    @Test
    fun showBeforeAttachStillDraws() {
        onMain {
            pane.show(course, MapStyle.SATELLITE)
            attach()
        }

        awaitLine()
    }

    @Test
    fun drawsAgainAfterTheViewComesBack() {
        onMain {
            attach()
            pane.show(course, MapStyle.SATELLITE)
        }
        awaitLine()

        onMain { detach() }
        onMain { attach() }

        awaitLine()
    }

    @Test
    fun switchingStyleKeepsTheLine() {
        onMain {
            attach()
            pane.show(course, MapStyle.SATELLITE)
        }
        awaitLine()

        onMain { pane.show(course, MapStyle.STREET) }
        onMain { pane.show(course, MapStyle.SATELLITE) }

        awaitLine()
    }

    @Test
    fun releaseWithoutEverAttachingIsSafe() {
        onMain {
            pane.release()
            pane.release()
            pane.show(course, MapStyle.STREET)
        }
    }

    @Test
    fun releaseWhileAttachedThenDetachIsSafe() {
        onMain {
            attach()
            pane.show(course, MapStyle.SATELLITE)
        }
        awaitLine()

        onMain {
            pane.release()
            detach()
        }
    }

    private fun clearMapCache() {
        val cleared = CountDownLatch(1)
        onMain {
            OfflineManager.getInstance(app).clearAmbientCache(object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = cleared.countDown()
                override fun onError(message: String) = cleared.countDown()
            })
        }
        assertTrue(cleared.await(WAIT_MS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun streetStyleThatArrivesLateStillFitsTheCourse() {
        clearMapCache()
        val reachable = AtomicBoolean(false)
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                if (!reachable.get()) throw UnknownHostException("no network")
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(EMPTY_STYLE.toResponseBody("application/json".toMediaType())).build()
            }.build(),
        )
        onMain {
            attach()
            pane.show(course, MapStyle.STREET)
        }
        Thread.sleep(OFFLINE_MS)

        reachable.set(true)

        awaitLine()
    }

    private companion object {
        const val EMPTY_STYLE = """{"version":8,"sources":{},"layers":[]}"""
        const val OFFLINE_MS = 1_500L
        val MOODS = listOf("#7CC48F", "#A3B3D1", "#5FC6D8")
        const val WIDTH = 1200
        const val HEIGHT = 700
        const val MIN_PIXELS = 40
        const val WAIT_MS = 15_000L
    }
}
