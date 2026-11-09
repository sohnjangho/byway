package com.byway.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.view.children
import com.byway.core.course.Course
import com.byway.core.course.EnergyRate
import com.byway.core.course.FrameInfo
import com.byway.core.course.Pace
import com.byway.core.course.paceOf
import com.byway.core.course.viewCount
import com.byway.core.course.SceneMood
import com.byway.core.course.ScenePlace
import com.byway.core.course.Weather
import com.byway.core.course.moodFor
import com.byway.service.MyDrives
import com.byway.service.SceneCatalog
import java.util.Calendar

/** 정차 중 첫 화면. 왼쪽은 코스 이름과 설명, 주변 정보이고 오른쪽은 지도 장면과 경로다. */
internal fun homeScreen(
    context: Context,
    kit: BrowseKit,
    courseId: String?,
    weather: Weather,
    stripX: Int?,
    reveal: Boolean,
    energy: EnergyRate?,
    onPick: (Course, Int) -> Unit,
    onStart: (String) -> Unit,
): View = with(context) {
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val mood = moodFor(weather, hour, now.get(Calendar.MONTH) + 1)
    val course = kit.book.courses.firstOrNull { it.id == courseId } ?: kit.book.suggested(mood, hour)
    val scene = SceneCatalog.of(this, course.id)
    val places = shownPlaces(scene?.places.orEmpty())
    val shownNo = shownNumbers(scene?.places.orEmpty())
    val third = kit.window == WindowClass.THIRD
    val full = kit.window == WindowClass.FULL

    FrameLayout(this).apply {
        setBackgroundColor(palette.bg)
        val hasScenery = canSwipeToScenery(course.sceneryCredit, Covers.hasScenery(context, course))
        val control = sceneryControl(kit.window, home = true)
        val hasFrames = scene != null && scene.frames.isNotEmpty() && scene.track.isNotEmpty()
        val sceneView = SceneView(context, kit.window, hintOnMap = control == SceneryControl.CHIP, strip = third && hasFrames)
        val sceneryButton = if (hasScenery && control == SceneryControl.BUTTON) compactButton("풍경") { sceneView.togglePhoto() } else null
        sceneView.onPhoto = { shown -> sceneryButton?.text = if (shown) "경로" else "풍경" }
        sceneView.show(course, scene, places, night, hasScenery, reveal)
        addView(sceneView, FrameLayout.LayoutParams(MATCH, if (third) dp(Dimens.THIRD_SCENE_HEIGHT) else MATCH))

        val effect = MoodEffectView(context, third)
        effect.show(if (MyDrives.moodOn) mood else SceneMood.PLAIN, night)
        val effectSize = when {
            third -> FrameLayout.LayoutParams(MATCH, dp(Dimens.THIRD_EFFECT_HEIGHT), Gravity.BOTTOM)
            else -> FrameLayout.LayoutParams(dp(if (full) Dimens.EFFECT_WIDTH_FULL else Dimens.EFFECT_WIDTH), MATCH)
        }
        addView(effect, effectSize)

        val pace = paceOf(kit.book.facts(course).meters, kit.book.facts(course).curves, scene?.places.orEmpty().map { it.kind })
        val choice = choiceFacts(
            course, kit.book.facts(course), pace.light,
            now.get(Calendar.DAY_OF_YEAR), hour * 60 + now.get(Calendar.MINUTE), energy,
        )
        val info = infoColumn(kit, course, kickFor(mood), pace, choice)
        val badges = mutableMapOf<Int, TextView>()
        val drawer = CourseDrawer(context, kit, course) { onPick(it, stripX ?: 0) }
        val near = nearColumn(kit, course, places, badges, sceneryButton, onStart, drawer::open)
        // 1/3 창은 사진 띠 때문에 지도 칸이 좁다. 긴 출처가 경로를 가리지 않게 짧게 적는다.
        sceneView.setCredit(if (third && hasFrames) shortMapCredit(currentMapStyle()) else mapCredit(currentMapStyle(), withFrames = hasFrames))
        // 훑어보기 자료의 장소 번호는 도구가 매긴 것이다. 목록의 번호로 바꿔서 쓴다.
        fun nearNo(frame: FrameInfo): Int? = frame.placeNo?.let { shownNo[it] }
        fun nearName(frame: FrameInfo): String? = nearNo(frame)?.let { no -> places.firstOrNull { it.no == no }?.name }
        fun onScrub(km: Double, frame: FrameInfo) {
            sceneView.setMarker(trackAt(scene?.track.orEmpty(), km))
            val near = nearNo(frame)
            badges.forEach { (no, badge) -> paintBadge(badge, no == near) }
        }
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
        if (reveal) staggerIn(linesOf(info, near), dp(Dimens.SPACE_3).toFloat())

        val left = dp(Dimens.RAIL_WIDTH + Dimens.SPACE_8)
        when {
            // 1/3 창은 띠 옆에 버튼을 둘 자리가 없다. 훑어보기만 지도 아래쪽에 얹고 서랍은 버튼 줄에서 연다.
            third -> if (scene != null && hasFrames) {
                addView(
                    ScrubStrip(context, course.id, scene, null, ::nearName, ::onScrub),
                    FrameLayout.LayoutParams(MATCH, WRAP).apply {
                        topMargin = dp(Dimens.THIRD_SCENE_HEIGHT - ScrubStrip.HEIGHT - Dimens.SPACE_3)
                        leftMargin = dp(Dimens.SPACE_6)
                    },
                )
            }
            scene != null && hasFrames -> addView(
                ScrubStrip(context, course.id, scene, drawerButton(drawer::open), ::nearName, ::onScrub),
                FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
                    leftMargin = left
                    bottomMargin = dp(Dimens.SPACE_2)
                },
            )
            else -> addView(
                courseStrip(kit, course, stripX, onPick),
                FrameLayout.LayoutParams(MATCH, dp(Dimens.STRIP_CARD_HEIGHT), Gravity.BOTTOM).apply {
                    leftMargin = left
                    bottomMargin = dp(Dimens.SPACE_6)
                },
            )
        }
        addView(drawer, matchFrame())
    }
}

/** 다른 코스 서랍을 여는 버튼. 운전석에서 가까운 띠의 왼쪽 끝에 둔다. */
private fun Context.drawerButton(onClick: () -> Unit): View =
    quietButton("다른\n코스", onClick).apply {
        textSize = Dimens.LABEL
        setPadding(0, 0, 0, 0)
    }

/** 코스 주변 번호. 훑어보는 지점과 가까운 곳은 주황으로 바꾼다. */
private fun Context.paintBadge(badge: TextView, near: Boolean) {
    badge.setTextColor(if (near) palette.onAccent else palette.bg)
    badge.background = rounded(if (near) palette.accent else palette.ink, Dimens.PLACE_NUMBER / 2)
}

/** 코스를 바꿀 때 차례로 나타나는 줄. 아래 버튼은 자리를 지킨다. */
private fun linesOf(info: View, near: View): List<View> =
    (info as ViewGroup).children.toList() + (near as ViewGroup).children.toList().dropLast(1).filterNot { it is Space }

private fun Context.infoColumn(kit: BrowseKit, course: Course, kick: String, pace: Pace, choice: List<Fact>): View = column().apply {
    val third = kit.window == WindowClass.THIRD
    val facts = kit.book.facts(course)
    if (!third) addView(label(kick, Dimens.SMALL, palette.inkSecondary).atMostLines(1))
    addView(label(course.name, if (third) Dimens.SIDE_NAME_THIRD else Dimens.SIDE_NAME, bold = true).atMostLines(1))
    addSpaced(pickRow(course.tags), Dimens.SPACE_2)
    if (!third) addSpaced(label(course.summary, Dimens.BODY).atMostLines(3), Dimens.SPACE_3)
    addSpaced(plainFacts(choice), Dimens.SPACE_3)
    addSpaced(label(sceneryLine(facts.moodShare, course.tags.themes, viewCount(course)), Dimens.LABEL).atMostLines(1), Dimens.SPACE_2)
    addSpaced(label("적합도·즐거움·소요 시간은 예상 · 교통 상황 제외", Dimens.LABEL, palette.inkSecondary).atMostLines(1), Dimens.SPACE_3)
    addSpaced(paceRow(pace, withNotes = !third), Dimens.SPACE_2)
}

/** 세 가지 예상 시간. 좁은 창은 설명 줄을 뺀다. */
private fun Context.paceRow(pace: Pace, withNotes: Boolean): View = LinearLayout(this).apply {
    paceCells(pace).forEach { item ->
        val cell = column().apply {
            addView(label(item.time, Dimens.SMALL, bold = true).tabular().atMostLines(1))
            addView(label(item.name, Dimens.LABEL, palette.ink).atMostLines(1))
            if (withNotes) addView(label(item.note, Dimens.LABEL, palette.inkSecondary).atMostLines(1))
        }
        addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
    }
}

/** 시안처럼 칸 없이 값과 이름만 놓는다. 칸 수가 차종마다 달라 글 폭대로 두고 남는 자리를 고르게 나눈다. */
private fun Context.plainFacts(facts: List<Fact>): View = LinearLayout(this).apply {
    facts.forEachIndexed { i, fact ->
        if (i > 0) addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        val cell = column().apply {
            addView(label(withSmallUnit(fact.value), Dimens.HEADING, bold = true).tabular().atMostLines(1))
            addView(label(fact.name, Dimens.LABEL, palette.ink).atMostLines(1))
        }
        addView(cell, LinearLayout.LayoutParams(WRAP, WRAP))
    }
}

private fun Context.nearColumn(
    kit: BrowseKit,
    course: Course,
    places: List<ScenePlace>,
    badges: MutableMap<Int, TextView>,
    sceneryButton: View?,
    onStart: (String) -> Unit,
    onOthers: () -> Unit,
): View = column().apply {
    val third = kit.window == WindowClass.THIRD
    if (!third && places.isNotEmpty()) addView(label("코스 주변", Dimens.SMALL, bold = true))
    val list = column()
    places.forEachIndexed { i, place -> list.addSpaced(placeRow(place, badges), if (i == 0) 0 else Dimens.SPACE_2) }
    val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(list)
    }.bounceAtEnds(vertical = true)
    addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(Dimens.SPACE_2) })
    addSpaced(Space(context), Dimens.SPACE_2)
    val start = primaryButton("내비에 코스 입히기") { onStart(course.id) }
    val buttons = LinearLayout(context).apply {
        addView(start, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
        if (third) {
            addView(quietButton("코스 바꾸기", onOthers), LinearLayout.LayoutParams(WRAP, MATCH).apply { marginEnd = dp(Dimens.SPACE_3) })
        }
        sceneryButton?.let { addView(it, LinearLayout.LayoutParams(WRAP, MATCH).apply { marginEnd = dp(Dimens.SPACE_3) }) }
        val save = if (third) saveToggle(course, Context::quietButton) else saveToggle(course, Context::compactButton, save = "저장")
        addView(save, LinearLayout.LayoutParams(WRAP, MATCH))
    }
    addView(buttons)
}

private fun Context.placeRow(place: ScenePlace, badges: MutableMap<Int, TextView>): View = row().apply {
    val number = label("${place.no}", Dimens.LABEL, bold = true).tabular().apply { gravity = Gravity.CENTER }
    paintBadge(number, near = false)
    badges[place.no] = number
    addView(number, LinearLayout.LayoutParams(dp(Dimens.PLACE_NUMBER), dp(Dimens.PLACE_NUMBER)).apply { marginEnd = dp(Dimens.SPACE_3) })
    val text = column().apply {
        addView(label(place.name, Dimens.SMALL, bold = true).atMostLines(1))
        addView(label(placeLine(place), Dimens.LABEL, palette.ink).atMostLines(1))
    }
    addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
}
