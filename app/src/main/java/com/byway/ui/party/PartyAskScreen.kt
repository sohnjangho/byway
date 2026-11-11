package com.byway.ui.party

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.share.PartyView
import com.byway.service.MyParty
import com.byway.service.PartyWatch
import com.byway.service.Share
import com.byway.service.ShareError
import com.byway.ui.BrowseKit
import com.byway.ui.CoursePathView
import com.byway.ui.Dimens
import com.byway.ui.MATCH
import com.byway.ui.MoodLayer
import com.byway.ui.WRAP
import com.byway.ui.addSpaced
import com.byway.ui.column
import com.byway.ui.dp
import com.byway.ui.fill
import com.byway.ui.kmText
import com.byway.ui.label
import com.byway.ui.palette
import com.byway.ui.panes
import com.byway.ui.primaryButton
import com.byway.ui.quietButton
import com.byway.ui.share.OFFLINE_NOTE

/**
 * 리더가 고른 코스를 승낙할지 묻는 정차 화면(함께달리기 설계 6-6). 승낙하면 위치 공유를 켜고 출발하고([onStart]),
 * 거부하면 파티를 나간다([onLeft]). [course]가 아직 기기에 없으면 이름 자리에 받는 중이라고 적는다.
 */
internal fun partyAskScreen(context: Context, kit: BrowseKit, view: PartyView, course: Course?, onStart: (String) -> Unit, onLeft: () -> Unit): View = with(context) {
    val mood = MoodLayer(context, kit.window)
    mood.show(course)
    val drawing = CoursePathView(this)
    course?.let { drawing.show(it, emptyList()) } ?: run { drawing.visibility = View.INVISIBLE }
    val note = label("", Dimens.SMALL, palette.accent)
    val text = column().apply {
        addView(label("함께달리기 · ${view.name}", Dimens.SMALL, palette.inkSecondary))
        addSpaced(label(askTitle(view.leaderName), Dimens.HEADING, bold = true), Dimens.SPACE_2)
        val line = course?.let { "${it.name} · ${kmText(it.path.zipWithNext { a, b -> a.distanceTo(b) }.sum())}" } ?: "코스를 받는 중입니다"
        addSpaced(label(line, Dimens.BODY), Dimens.SPACE_3)
        addSpaced(label("위치 공유", Dimens.SMALL, bold = true), Dimens.SPACE_6)
        LOCATION_CONSENT.forEach { addSpaced(label("· $it", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_2) }
        addSpaced(label("거부하면 이 파티에서 나갑니다.", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_4)
    }

    fun answer(accept: Boolean) {
        val code = view.code
        val courseId = view.courseId ?: return
        note.text = "서버와 주고받는 중"
        Share.io.execute {
            val client = Share.client(context)
            val result = client.answer(code, view.proposal, accept).mapCatching { if (accept) client.sharing(code, true).getOrThrow() }
            text.post {
                val problem = result.exceptionOrNull()
                when {
                    // 그사이 리더가 다시 골랐다. 새 제안을 다시 묻는다.
                    (problem as? ShareError.Http)?.code == 409 -> {
                        note.text = "리더가 코스를 다시 골랐습니다"
                        PartyWatch.poke()
                    }
                    problem != null -> note.text = if (problem is ShareError.Http) problem.message.orEmpty() else OFFLINE_NOTE
                    accept -> {
                        PartyWatch.accepted()
                        onStart(courseId)
                    }
                    else -> {
                        MyParty.set(context, null)
                        PartyWatch.forget()
                        onLeft()
                    }
                }
            }
        }
    }

    val actions = LinearLayout(context).apply {
        addView(primaryButton(ACCEPT_BUTTON) { answer(accept = true) }, LinearLayout.LayoutParams(0, WRAP, 1.4f).apply { marginEnd = dp(Dimens.SPACE_3) })
        addView(quietButton(DECLINE_BUTTON) { answer(accept = false) }, LinearLayout.LayoutParams(0, MATCH, 1f))
    }
    val left = column().apply {
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(text)
        }, fill())
        addSpaced(note, Dimens.SPACE_2)
        addSpaced(actions, Dimens.SPACE_3)
    }
    val edge = dp(kit.edge)
    mood.hold(panes(left, drawing, kit.wide).apply { setPadding(edge, edge, edge, edge) })
}
