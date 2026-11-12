package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import com.byway.core.course.Course
import com.byway.core.course.SceneMood
import com.byway.core.course.ScenePlace
import com.byway.core.course.Weather
import com.byway.core.course.moodFor
import com.byway.service.MyDrives
import com.byway.service.SceneCatalog
import java.util.Calendar

/** 정차 중 첫 화면. 왼쪽은 코스 이름과 설명, 주변 정보이고 오른쪽은 위성 장면과 경로다. */
internal fun homeScreen(
    context: Context,
    kit: BrowseKit,
    courseId: String?,
    weather: Weather,
    stripX: Int?,
    onPick: (Course, Int) -> Unit,
    onStart: (String) -> Unit,
    onBrowse: () -> Unit,
): View = with(context) {
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val mood = moodFor(weather, hour, now.get(Calendar.MONTH) + 1)
    val course = kit.book.courses.firstOrNull { it.id == courseId } ?: kit.book.suggested(mood, hour)
    val scene = SceneCatalog.of(this, course.id)
    val places = shownPlaces(scene?.places.orEmpty())
    val third = kit.window == WindowClass.THIRD
    val full = kit.window == WindowClass.FULL

    FrameLayout(this).apply {
        setBackgroundColor(palette.bg)
        val sceneView = SceneView(context, kit.window)
        sceneView.show(course, scene, places, night, canSwipeToScenery(course.sceneryCredit, Covers.hasScenery(context, course)))
        addView(sceneView, FrameLayout.LayoutParams(MATCH, if (third) dp(Dimens.THIRD_SCENE_HEIGHT) else MATCH))

        val soft = SoftPatch(context, third)
        Covers.loadSoft(context, course.id) { soft.setPicture(it) }
        val softSize = when {
            third -> FrameLayout.LayoutParams(MATCH, dp(Dimens.THIRD_SOFT_HEIGHT), Gravity.BOTTOM)
            else -> FrameLayout.LayoutParams(dp(if (full) Dimens.SOFT_WIDTH_FULL else Dimens.SOFT_WIDTH), dp(Dimens.SOFT_HEIGHT)).apply {
                leftMargin = dp(Dimens.RAIL_WIDTH)
            }
        }
        addView(soft, softSize)

        val effect = MoodEffectView(context, third)
        effect.show(if (MyDrives.moodOn) mood else SceneMood.PLAIN, night)
        val effectSize = when {
            third -> FrameLayout.LayoutParams(MATCH, dp(Dimens.THIRD_EFFECT_HEIGHT), Gravity.BOTTOM)
            else -> FrameLayout.LayoutParams(dp(if (full) Dimens.EFFECT_WIDTH_FULL else Dimens.EFFECT_WIDTH), MATCH)
        }
        addView(effect, effectSize)

        val info = infoColumn(kit, course, kickFor(mood))
        val near = nearColumn(kit, course, places, onStart, onBrowse)
        val side = when {
            full -> LinearLayout(context).apply {
                addView(info, LinearLayout.LayoutParams(dp(Dimens.SIDE_INFO_WIDTH), MATCH).apply { marginEnd = dp(Dimens.SPACE_10) })
                addView(near, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            else -> column().apply {
                addView(info)
                addView(near, fill().apply { topMargin = dp(Dimens.SPACE_3) })
            }
        }
        // 글 칸의 빈 곳을 눌러도 뒤의 사진이 넘어가지 않게 한다.
        side.isClickable = true
        val edge = dp(if (third) Dimens.SPACE_6 else Dimens.SPACE_8)
        side.setPadding(edge, if (third) dp(Dimens.SPACE_2) else edge, edge, if (third) dp(Dimens.SPACE_6) else dp(Dimens.SPACE_4))
        val sideSize = when {
            third -> FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = dp(Dimens.THIRD_SCENE_HEIGHT) }
            else -> FrameLayout.LayoutParams(dp(if (full) Dimens.SIDE_WIDTH_FULL else Dimens.SIDE_WIDTH), MATCH).apply {
                leftMargin = dp(Dimens.RAIL_WIDTH)
                bottomMargin = dp(Dimens.SIDE_BOTTOM)
            }
        }
        addView(side, sideSize)

        if (!third) {
            val strip = courseStrip(kit, course, stripX, onPick)
            addView(
                strip,
                FrameLayout.LayoutParams(MATCH, dp(Dimens.STRIP_CARD_HEIGHT), Gravity.BOTTOM).apply {
                    leftMargin = dp(Dimens.RAIL_WIDTH + Dimens.SPACE_8)
                    bottomMargin = dp(Dimens.SPACE_6)
                },
            )
        }
    }
}

private fun Context.infoColumn(kit: BrowseKit, course: Course, kick: String): View = column().apply {
    val third = kit.window == WindowClass.THIRD
    val facts = kit.book.facts(course)
    if (!third) addView(label(kick, Dimens.SMALL, palette.accent).atMostLines(1))
    addView(label(course.name, if (third) Dimens.SIDE_NAME_THIRD else Dimens.SIDE_NAME, bold = true).atMostLines(1))
    addSpaced(pickRow(course.tags), Dimens.SPACE_2)
    if (!third) addSpaced(label(course.summary, Dimens.BODY).atMostLines(3), Dimens.SPACE_3)
    val cells = listOf(
        Fact(kmText(facts.meters), "거리"),
        Fact("${facts.curves}개", "굽이"),
        Fact(ratioText(facts), "에움 배율"),
        Fact(climbText(course, facts), "오르막"),
    )
    addSpaced(plainFacts(cells), Dimens.SPACE_3)
}

/** 시안처럼 칸 없이 값과 이름만 놓는다. */
private fun Context.plainFacts(facts: List<Fact>): View = LinearLayout(this).apply {
    facts.forEach { fact ->
        val cell = column().apply {
            addView(label(withSmallUnit(fact.value), Dimens.VALUE, bold = true).tabular().atMostLines(1))
            addView(label(fact.name, Dimens.LABEL, palette.ink).atMostLines(1))
        }
        addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
    }
}

private fun Context.nearColumn(
    kit: BrowseKit,
    course: Course,
    places: List<ScenePlace>,
    onStart: (String) -> Unit,
    onBrowse: () -> Unit,
): View = column().apply {
    val third = kit.window == WindowClass.THIRD
    if (!third && places.isNotEmpty()) addView(label("코스 주변", Dimens.SMALL, bold = true))
    val list = column()
    places.forEachIndexed { i, place -> list.addSpaced(placeRow(place), if (i == 0) 0 else Dimens.SPACE_2) }
    val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(list)
    }
    addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(Dimens.SPACE_2) })
    addSpaced(Space(context), Dimens.SPACE_2)
    val start = primaryButton("내비에 코스 입히기") { onStart(course.id) }
    val buttons = LinearLayout(context).apply {
        addView(start, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
        if (third) {
            addView(quietButton("코스 바꾸기", onBrowse), LinearLayout.LayoutParams(WRAP, MATCH).apply { marginEnd = dp(Dimens.SPACE_3) })
        }
        addView(saveToggle(course, Context::quietButton), LinearLayout.LayoutParams(WRAP, MATCH))
    }
    addView(buttons)
}

private fun Context.placeRow(place: ScenePlace): View = row().apply {
    val number = label("${place.no}", Dimens.LABEL, palette.bg, bold = true).tabular().apply {
        gravity = Gravity.CENTER
        background = rounded(palette.ink, Dimens.PLACE_NUMBER / 2)
    }
    addView(number, LinearLayout.LayoutParams(dp(Dimens.PLACE_NUMBER), dp(Dimens.PLACE_NUMBER)).apply { marginEnd = dp(Dimens.SPACE_3) })
    val text = column().apply {
        addView(label(place.name, Dimens.SMALL, bold = true).atMostLines(1))
        addView(label(placeLine(place), Dimens.LABEL, palette.ink).atMostLines(1))
    }
    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
}
