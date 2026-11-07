package com.byway.platform

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.byway.core.course.Course
import com.byway.service.MapColors
import com.byway.service.MapPane
import com.byway.service.MapStyle
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.constants.MapLibreConstants
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** 코스 상세의 지도 칸. MapLibre 타입은 이 파일과 [courseLines] 밖으로 나가지 않는다. 뷰가 창에 붙어 있는 동안 화면 생명주기를 따른다. */
internal class CourseMapPane(context: Context, private val colors: MapColors) : MapPane {
    private val density = context.resources.displayMetrics.density
    private val fitPadding = (FIT_PADDING_DP * density).toInt()
    private val mapView = newMapView(context, colors.blank)
    private var map: MapLibreMap? = null
    private var course: Course? = null
    private var style = MapStyle.STREET
    private var fittedCourseId: String? = null
    private var following: Lifecycle? = null
    private var released = false

    private val follower = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> mapView.onStart()
            Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
            Lifecycle.Event.ON_STOP -> mapView.onStop()
            else -> Unit
        }
    }

    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(attached: View) {
            following = attached.findViewTreeLifecycleOwner()?.lifecycle?.also { it.addObserver(follower) }
        }

        override fun onViewDetachedFromWindow(detached: View) = stopFollowing()
    }

    override val view: View = FrameLayout(context).apply {
        setBackgroundColor(colors.blank)
        addView(mapView)
        addOnAttachStateChangeListener(attachment)
    }

    init {
        mapView.addOnDidFailLoadingMapListener { Log.w(TAG, "map style failed: $it") }
        mapView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) fit()
        }
        mapView.getMapAsync {
            map = it
            draw()
        }
    }

    override fun show(course: Course, style: MapStyle) {
        if (released) return
        this.course = course
        this.style = style
        draw()
    }

    override fun release() {
        if (released) return
        released = true
        view.removeOnAttachStateChangeListener(attachment)
        stopFollowing()
        map = null
        mapView.onDestroy()
    }

    /** 뷰가 화면에서 빠지면 화면 생명주기와 상관없이 지도를 세운다. */
    private fun stopFollowing() {
        val lifecycle = following ?: return
        following = null
        lifecycle.removeObserver(follower)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
    }

    private fun draw() {
        val map = map ?: return
        val course = course ?: return
        val maxZoom = if (style == MapStyle.SATELLITE) SATELLITE_MAX_ZOOM else MapLibreConstants.MAXIMUM_ZOOM
        map.setMaxZoomPreference(maxZoom.toDouble())
        map.setStyle(withCourse(baseStyle(style), course))
        // 스타일이 한 번 실패하면 setStyle 콜백은 다시 오지 않는다. 카메라는 스타일을 기다리지 않는다.
        if (fittedCourseId != course.id) fit()
    }

    private fun baseStyle(style: MapStyle): Style.Builder = when (style) {
        MapStyle.STREET -> Style.Builder().fromUri(STREET_STYLE)
        MapStyle.SATELLITE -> Style.Builder()
            .withLayer(BackgroundLayer(BLANK_LAYER).withProperties(backgroundColor(colors.blank)))
            .withSource(
                RasterSource(
                    SATELLITE_LAYER,
                    TileSet(TILE_JSON_VERSION, SATELLITE_TILES).apply { setMaxZoom(SATELLITE_MAX_ZOOM) },
                    SATELLITE_TILE_SIZE,
                ),
            )
            .withLayer(RasterLayer(SATELLITE_LAYER, SATELLITE_LAYER))
    }

    private fun withCourse(base: Style.Builder, course: Course): Style.Builder = base
        .withSource(GeoJsonSource(COURSE_SOURCE, courseLines(course, colors)))
        .withLayer(
            LineLayer(COURSE_OUTLINE_LAYER, COURSE_SOURCE)
                .withProperties(lineColor(colors.outline), lineWidth(OUTLINE_WIDTH), *ROUND_LINE),
        )
        .withLayer(
            LineLayer(COURSE_LINE_LAYER, COURSE_SOURCE)
                .withProperties(lineColor(toColor(get(LINE_COLOR))), lineWidth(LINE_WIDTH), *ROUND_LINE),
        )

    private fun fit() {
        val map = map ?: return
        val course = course ?: return
        if (mapView.width == 0 || mapView.height == 0) return
        val bounds = LatLngBounds.Builder().includes(course.path.map { LatLng(it.lat, it.lon) }).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, fitPadding))
        fittedCourseId = course.id
    }

    private companion object {
        const val TAG = "DRIVE"
        const val STREET_STYLE = "https://tiles.openfreemap.org/styles/liberty"
        const val SATELLITE_TILES =
            "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/{z}/{y}/{x}.jpg"
        const val SATELLITE_TILE_SIZE = 256
        const val SATELLITE_MAX_ZOOM = 14f
        const val TILE_JSON_VERSION = "2.2.0"

        const val BLANK_LAYER = "blank"
        const val SATELLITE_LAYER = "satellite"
        const val COURSE_SOURCE = "course"
        const val COURSE_OUTLINE_LAYER = "course-outline"
        const val COURSE_LINE_LAYER = "course-line"

        const val OUTLINE_WIDTH = 8f
        const val LINE_WIDTH = 5f
        const val FIT_PADDING_DP = 48
        const val MAX_REQUESTS_PER_HOST = 6
        val ROUND_LINE = arrayOf(lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND))

        /** 위성 타일 서버는 요청이 몰리면 연결을 끊은 적이 있다. MapLibre 기본값은 호스트당 20개다. */
        val GENTLE_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
            .addInterceptor(RetryDropped())
            .build()

        fun newMapView(context: Context, blank: Int): MapView {
            MapLibre.getInstance(context)
            HttpRequestUtil.setOkHttpClient(GENTLE_CLIENT)
            val options = MapLibreMapOptions.createFromAttributes(context)
                .textureMode(true)
                .logoEnabled(false)
                .attributionEnabled(false)
                .foregroundLoadColor(blank)
            return MapView(context, options).apply { onCreate(null) }
        }
    }
}
