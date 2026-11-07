package com.byway.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.byway.core.course.Course
import com.byway.core.course.Stop
import com.byway.service.MapPanes
import com.byway.service.MapStyle

/**
 * 코스 상세의 그림 칸. 코스 그림과 지도, 위성을 바꿔 본다.
 * 지도를 쓸 수 없으면 전환 버튼 없이 코스 그림만 둔다. 지도는 정차 화면에만 쓰고 칸이 사라질 때 놓는다.
 */
@SuppressLint("ViewConstructor")
internal class PicturePane(
    context: Context,
    private val course: Course,
    stops: List<Stop>,
    picture: Picture,
    private val onPick: (Picture) -> Unit,
) : FrameLayout(context) {
    private val colors = context.palette
    private val map = MapPanes.create(context, colors.mapColors())
    private val drawing = CoursePathView(context).apply { show(course, stops) }
    private val credit = context.label("", Dimens.LABEL, colors.onPhoto).apply {
        background = context.rounded(colors.photoScrim, Dimens.SPACE_3)
        val inset = context.dp(Dimens.CREDIT_PADDING)
        setPadding(context.dp(Dimens.SPACE_3), inset, context.dp(Dimens.SPACE_3), inset)
    }
    private val buttons = mutableMapOf<Picture, TextView>()

    init {
        asCard()
        clipToOutline = true
        // 전환 버튼이 코스 선을 가리지 않게 버튼 아래부터 그린다.
        val top = if (map != null) context.dp(Dimens.TOUCH_MIN + Dimens.SPACE_6) else 0
        addView(drawing, matchFrame().apply { topMargin = top })
        if (map != null) {
            addView(map.view, matchFrame())
            addView(
                credit,
                LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply {
                    marginStart = context.dp(Dimens.SPACE_4)
                    marginEnd = context.dp(Dimens.SPACE_4)
                    bottomMargin = context.dp(Dimens.SPACE_3)
                },
            )
            addView(
                switcher(),
                LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
                    topMargin = context.dp(Dimens.SPACE_6)
                    marginEnd = context.dp(Dimens.SPACE_6)
                },
            )
            show(picture)
        }
    }

    /** 흐림 효과 없이 단색 반투명 바탕을 쓴다. */
    private fun switcher(): View = LinearLayout(context).apply {
        background = context.rounded(colors.photoScrim)
        clipToOutline = true
        Picture.entries.forEach { picture ->
            val button = context.label(picture.title, Dimens.SMALL, colors.onPhoto).apply {
                gravity = Gravity.CENTER
                setPadding(context.dp(Dimens.SPACE_6), 0, context.dp(Dimens.SPACE_6), 0)
                setOnClickListener {
                    show(picture)
                    onPick(picture)
                }
            }
            buttons[picture] = button
            addView(button, LinearLayout.LayoutParams(WRAP, context.dp(Dimens.TOUCH_MIN)))
        }
    }

    private fun show(picture: Picture) {
        val pane = map ?: return
        buttons.forEach { (kind, button) ->
            val on = kind == picture
            button.setBackgroundColor(if (on) colors.accent else Color.TRANSPARENT)
            button.setTextColor(if (on) colors.onAccent else colors.onPhoto)
            button.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        drawing.visibility = if (picture == Picture.DRAWING) VISIBLE else GONE
        pane.view.visibility = if (picture == Picture.DRAWING) GONE else VISIBLE
        credit.visibility = if (picture == Picture.DRAWING) GONE else VISIBLE
        when (picture) {
            Picture.DRAWING -> Unit
            Picture.STREET -> {
                pane.show(course, MapStyle.STREET)
                credit.text = MapStyle.STREET.credit
            }
            Picture.SATELLITE -> {
                pane.show(course, MapStyle.SATELLITE)
                credit.text = MapStyle.SATELLITE.credit
            }
        }
    }

    override fun onDetachedFromWindow() {
        map?.release()
        super.onDetachedFromWindow()
    }
}
