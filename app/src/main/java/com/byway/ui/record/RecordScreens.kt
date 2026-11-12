package com.byway.ui.record

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import com.byway.core.course.Course
import com.byway.core.geo.GeoPoint
import com.byway.core.record.Recorded
import com.byway.core.record.makeCourse
import com.byway.core.share.newCode
import com.byway.service.CourseCatalog
import com.byway.service.CourseStore
import com.byway.service.DriveState
import com.byway.ui.BrowseKit
import com.byway.ui.Choice
import com.byway.ui.CoursePathView
import com.byway.ui.Dimens
import com.byway.ui.MATCH
import com.byway.ui.WRAP
import com.byway.ui.WindowClass
import com.byway.ui.addSpaced
import com.byway.ui.asCard
import com.byway.ui.chipRow
import com.byway.ui.column
import com.byway.ui.dp
import com.byway.ui.fill
import com.byway.ui.kmText
import com.byway.ui.label
import com.byway.ui.driveButton
import com.byway.ui.palette
import com.byway.ui.panes
import com.byway.ui.primaryButton
import com.byway.ui.quietButton
import com.byway.ui.tabular
import com.byway.ui.textField
import kotlin.random.Random

/** 기록 동의(공유 설계 6-3). 동의 버튼만 동의로 본다. 뒤로 가기는 [onCancel]과 같다. */
internal val RECORD_CONSENT_LINES = listOf(
    "기록하는 동안 위치를 1초마다 이 차에만 저장합니다.",
    "공유를 누르기 전에는 기록이 차 밖으로 나가지 않습니다.",
    "끝내면 처음과 끝 500m를 잘라 집 위치를 가립니다.",
    "5km보다 짧은 기록은 저장하지 않습니다.",
    "저장한 코스는 내 코스에서 언제든 지울 수 있습니다.",
)

internal val RECORD_MOOD_CHOICES = listOf(Choice("forest", "숲길"), Choice("city", "도시"), Choice("river", "물가"))

internal fun recordConsentScreen(context: Context, kit: BrowseKit, onAgree: () -> Unit, onCancel: () -> Unit): View = with(context) {
    column().apply {
        setBackgroundColor(palette.bg)
        val edge = dp(kit.edge)
        setPadding(edge, edge, edge, edge)
        addView(label("길 기록하기", Dimens.SMALL, palette.inkSecondary))
        addSpaced(label("달린 길을 코스로 남깁니다", Dimens.HEADING, bold = true), Dimens.SPACE_3)
        RECORD_CONSENT_LINES.forEach { addSpaced(label("· $it", Dimens.BODY, palette.inkSecondary), Dimens.SPACE_3) }
        addView(Space(context), fill())
        val buttons = LinearLayout(context).apply {
            addView(primaryButton("동의하고 기록 시작", onAgree), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_4) })
            addView(quietButton("동의하지 않음", onCancel), LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        addSpaced(buttons, Dimens.SPACE_6)
    }
}

/** 기록 중 화면. 주행 중에도 보이므로 한 줄과 버튼 1개뿐이다. */
@SuppressLint("ViewConstructor")
internal class RecordingView(context: Context, window: WindowClass, onStop: () -> Unit) : FrameLayout(context) {
    private val distance = context.label("", Dimens.DRIVE_NUMBER, bold = true).tabular()

    init {
        setBackgroundColor(context.palette.bg)
        val body = context.column().apply {
            gravity = Gravity.CENTER
            val edge = context.dp(if (window == WindowClass.THIRD) Dimens.SPACE_6 else Dimens.SPACE_10)
            setPadding(edge, edge, edge, edge)
            addView(context.label("길을 기록하는 중", Dimens.DRIVE_SECTION, bold = true).apply { gravity = Gravity.CENTER })
            addSpaced(distance.apply { gravity = Gravity.CENTER }, Dimens.SPACE_4)
            addSpaced(context.driveButton("기록 끝내기", onStop), Dimens.SPACE_8)
        }
        addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    fun show(state: DriveState) {
        distance.text = kmText(state.recording?.meters ?: 0.0)
    }
}

/**
 * 기록을 끝낸 뒤 정차 화면. 이름과 분위기를 정해 내 코스에 저장한다.
 * 짧은 기록은 저장할 수 없다고만 알리고 버린다.
 */
internal fun saveRecordScreen(context: Context, kit: BrowseKit, fixes: List<GeoPoint>, onSaved: (Course) -> Unit, onDiscard: () -> Unit): View = with(context) {
    val preview = makeCourse("m-preview", "미리보기", "city", fixes)
    val text = column()
    text.addView(label("기록한 길", Dimens.SMALL, palette.inkSecondary))
    if (preview !is Recorded.Made) {
        val meters = (preview as? Recorded.TooShort)?.meters ?: 0.0
        text.addSpaced(label("5km보다 짧아 저장할 수 없습니다", Dimens.HEADING, bold = true), Dimens.SPACE_3)
        text.addSpaced(label("처음과 끝 500m를 빼고 ${kmText(meters)}를 달렸습니다", Dimens.BODY, palette.inkSecondary), Dimens.SPACE_3)
        text.addView(Space(context), fill())
        text.addSpaced(primaryButton("확인", onDiscard), Dimens.SPACE_6)
        return@with framed(kit, text)
    }
    val name = textField("코스 이름 (20자 안)", 20)
    var mood: String? = null
    val problem = label("", Dimens.SMALL, palette.accent)
    text.addSpaced(label("${kmText(preview.course.path.zipWithNext { a, b -> a.distanceTo(b) }.sum())} · 구간 ${preview.course.sections.size}개", Dimens.HEADING, bold = true), Dimens.SPACE_3)
    text.addSpaced(label("처음과 끝 500m는 잘라 냈습니다", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_2)
    text.addSpaced(name, Dimens.SPACE_6)
    text.addSpaced(label("코스 분위기", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_4)
    text.addSpaced(chipRow(RECORD_MOOD_CHOICES, null, Dimens.BODY) { mood = it }, Dimens.SPACE_2)
    text.addSpaced(problem, Dimens.SPACE_3)
    text.addView(Space(context), fill())
    val buttons = LinearLayout(context).apply {
        val save = primaryButton("내 코스에 저장") {
            val chosen = mood ?: return@primaryButton run { problem.text = "코스 분위기를 골라 주세요" }
            when (val made = makeCourse("m-" + newCode(Random.Default).lowercase(), name.text.toString(), chosen, fixes)) {
                is Recorded.Made -> {
                    CourseStore.files(context).saveMine(made.course)
                    CourseCatalog.invalidate()
                    onSaved(made.course)
                }
                is Recorded.BadName -> problem.text = made.reason
                is Recorded.TooShort -> problem.text = "5km보다 짧아 저장할 수 없습니다"
            }
        }
        addView(save, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_4) })
        addView(quietButton("버리기", onDiscard), LinearLayout.LayoutParams(0, MATCH, 1f))
    }
    text.addSpaced(buttons, Dimens.SPACE_6)
    val drawing = CoursePathView(this).apply {
        asCard()
        show(preview.course, emptyList())
    }
    val edge = dp(kit.edge)
    panes(text, drawing, kit.wide).apply {
        setBackgroundColor(palette.bg)
        setPadding(edge, edge, edge, edge)
    }
}

private fun Context.framed(kit: BrowseKit, content: View): View = FrameLayout(this).apply {
    setBackgroundColor(palette.bg)
    val edge = dp(kit.edge)
    setPadding(edge, edge, edge, edge)
    addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
}
