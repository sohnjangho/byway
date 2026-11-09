package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.byway.core.course.CourseScene
import com.byway.core.course.FrameInfo

private const val FRAME_WIDTH = 150
private const val FRAME_HEIGHT = 96
private const val FRAME_GAP = 3
private const val MOOD_BAR = 6
private const val PLAYHEAD_WIDTH = 5
private const val HEAD = 128

/**
 * 코스 훑어보기. 코스를 따라 고른 지점의 사진 띠다. 띠를 밀면 [onScrub]으로 코스 위 거리(km)와 그 지점의 사진 정보를 알린다.
 * 기준선은 운전석 쪽에 고정되어 있고 띠가 그 밑으로 지나간다. [leading]은 띠 왼쪽에 두는 버튼이다.
 * 사진은 지금 위성 조각이다. 차량 카메라 사진이 생기면 같은 이름의 파일만 바꾼다.
 */
@SuppressLint("ViewConstructor")
internal class ScrubStrip(
    context: Context,
    courseId: String,
    private val scene: CourseScene,
    leading: View?,
    private val onScrub: (Double, FrameInfo) -> Unit,
) : LinearLayout(context) {
    private val colors = context.palette
    private val km = context.label("", Dimens.SMALL, bold = true).tabular().atMostLines(1)
    private val where = context.label("", Dimens.LABEL).atMostLines(1)
    private val filmPx = context.dp(scene.frames.size * FRAME_WIDTH + (scene.frames.size - 1) * FRAME_GAP)

    init {
        orientation = VERTICAL
        val caption = context.row().apply {
            addView(km)
            addView(where, LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(Dimens.SPACE_3) })
        }
        addView(caption, LayoutParams(MATCH, context.dp(HEIGHT - FILM_HEIGHT)))

        val head = context.dp(HEAD)
        val row = LinearLayout(context)
        scene.frames.forEachIndexed { i, frame ->
            row.addView(frameCell(courseId, i, frame), LayoutParams(context.dp(FRAME_WIDTH), context.dp(FRAME_HEIGHT)).apply {
                if (i > 0) marginStart = context.dp(FRAME_GAP)
            })
        }
        val scroller = HorizontalScrollView(context).bounceAtEnds(vertical = false).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            setOnScrollChangeListener { _, x, _, _, _ -> report(x) }
        }
        val playhead = View(context).apply { background = context.rounded(colors.ink, PLAYHEAD_WIDTH / 2) }
        val film = FrameLayout(context).apply {
            addView(scroller, matchFrame())
            addView(playhead, FrameLayout.LayoutParams(context.dp(PLAYHEAD_WIDTH), MATCH).apply { leftMargin = head - context.dp(PLAYHEAD_WIDTH) / 2 })
            // 첫 사진과 끝 사진이 기준선까지 올 수 있게 양쪽을 비운다.
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> row.setPadding(head, 0, maxOf(0, view.width - head), 0) }
        }
        val band = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            leading?.let { addView(it, LayoutParams(context.dp(Dimens.STRIP_MODE_WIDTH), MATCH).apply { marginEnd = context.dp(Dimens.SPACE_3) }) }
            addView(film, LayoutParams(0, MATCH, 1f))
        }
        addView(band, LayoutParams(MATCH, context.dp(FILM_HEIGHT)))
        report(0)
    }

    private fun report(scrollX: Int) {
        val at = scrubKm(scrollX, filmPx, scene.totalKm)
        val frame = scene.frames[frameAt(scene.frames, at)]
        km.text = scrubKmText(at)
        where.text = scrubWhere(frame, scene.places.firstOrNull { it.no == frame.placeNo }?.name)
        onScrub(at, frame)
    }

    private fun frameCell(courseId: String, index: Int, frame: FrameInfo): View = FrameLayout(context).apply {
        background = context.rounded(colors.bgRaised, Dimens.RADIUS_SM)
        clipToOutline = true
        val picture = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        addView(picture, matchFrame())
        // 구간 분위기 색. 띠만 봐도 숲길, 도심, 강변이 어디서 바뀌는지 보인다.
        addView(View(context).apply { setBackgroundColor(NightPalette.moodColor(frame.mood)) }, FrameLayout.LayoutParams(MATCH, context.dp(MOOD_BAR), Gravity.BOTTOM))
        Covers.loadFrame(context, courseId, index) { picture.setImageBitmap(it) }
    }

    companion object {
        const val FILM_HEIGHT = 100
        /** 지점 글 한 줄과 사진 띠를 합친 높이. */
        const val HEIGHT = 136
    }
}
