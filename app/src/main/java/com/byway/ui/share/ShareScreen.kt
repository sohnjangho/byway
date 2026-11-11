package com.byway.ui.share

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import com.byway.core.course.Course
import com.byway.core.course.writeCourse
import com.byway.core.share.UploadCourse
import com.byway.core.share.Visibility
import com.byway.service.CourseStore
import com.byway.service.Share
import com.byway.service.ShareError
import com.byway.service.ShareSync
import com.byway.ui.BrowseKit
import com.byway.ui.Choice
import com.byway.ui.CoursePathView
import com.byway.ui.Dimens
import com.byway.ui.MATCH
import com.byway.ui.WRAP
import com.byway.ui.addSpaced
import com.byway.ui.asCard
import com.byway.ui.chipRow
import com.byway.ui.column
import com.byway.ui.dp
import com.byway.ui.fill
import com.byway.ui.label
import com.byway.ui.palette
import com.byway.ui.panes
import com.byway.ui.primaryButton
import com.byway.ui.quietButton

/**
 * 내가 만든 코스의 공유(공유 설계 6-4). 범위를 고르고 동의 버튼을 눌러야 올라간다. 정차 화면이다.
 * 파티 범위는 내가 멤버인 파티(열린 파티, 즐겨찾는 파티) 하나를 고른다.
 */
internal fun shareScreen(context: Context, kit: BrowseKit, course: Course, onDone: () -> Unit, onCancel: () -> Unit): View = with(context) {
    var scope: String? = null
    var party: String? = null
    val text = column()
    val hint = label("", Dimens.SMALL, palette.inkSecondary)
    val parties = FrameLayout(context)
    val consent = column()
    val problem = label("", Dimens.SMALL, palette.accent)
    fun showConsent() {
        consent.removeAllViews()
        scope?.let { chosen -> consentLines(chosen).forEach { consent.addSpaced(label("· $it", Dimens.BODY, palette.inkSecondary), Dimens.SPACE_2) } }
    }
    fun loadParties() {
        parties.removeAllViews()
        parties.addView(label("파티를 불러오는 중", Dimens.SMALL, palette.inkSecondary))
        Share.io.execute {
            val found = Share.client(context).parties()
            parties.post {
                parties.removeAllViews()
                val list = found.getOrNull()
                when {
                    list == null -> parties.addView(label(OFFLINE_NOTE, Dimens.SMALL, palette.inkSecondary))
                    list.isEmpty() -> parties.addView(label(PARTY_NEEDED, Dimens.SMALL, palette.inkSecondary))
                    else -> parties.addView(chipRow(list.map { Choice(it.code, it.name) }, null, Dimens.SMALL) { party = it })
                }
            }
        }
    }

    text.addView(label("코스 공유", Dimens.SMALL, palette.inkSecondary))
    text.addSpaced(label(course.name, Dimens.HEADING, bold = true), Dimens.SPACE_2)
    text.addSpaced(label("공유 범위", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_6)
    text.addSpaced(chipRow(SHARE_SCOPES.map { Choice(it.first, it.second) }, null, Dimens.BODY) { key ->
        scope = key
        party = null
        hint.text = scopeHint(key)
        problem.text = ""
        if (key == Visibility.PARTY) loadParties() else parties.removeAllViews()
        showConsent()
    }, Dimens.SPACE_2)
    text.addSpaced(hint, Dimens.SPACE_2)
    text.addSpaced(parties, Dimens.SPACE_2)
    text.addSpaced(consent, Dimens.SPACE_4)
    text.addSpaced(problem, Dimens.SPACE_3)
    text.addView(Space(context), fill())
    val buttons = LinearLayout(context)
    val agree = primaryButton("동의하고 공유") {
        val chosen = scope ?: return@primaryButton run { problem.text = "공유 범위를 골라 주세요" }
        if (chosen == Visibility.PARTY && party == null) return@primaryButton run { problem.text = "파티를 골라 주세요" }
        problem.text = "올리는 중"
        val upload = UploadCourse(chosen, party.takeIf { chosen == Visibility.PARTY }, writeCourse(course))
        Share.io.execute {
            val result = Share.client(context).upload(upload)
            text.post {
                result.onSuccess { uploaded ->
                    CourseStore.setUpload(context, course.id, uploaded.id)
                    ShareSync.refresh(context, force = true) {}
                    text.removeAllViews()
                    text.addView(label("공유했습니다", Dimens.HEADING, bold = true))
                    text.addSpaced(label("${scopeTitle(chosen)} · ${scopeHint(chosen)}", Dimens.BODY, palette.inkSecondary), Dimens.SPACE_3)
                    uploaded.code?.let { code ->
                        text.addSpaced(label("공유 코드", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_6)
                        text.addSpaced(label(code, Dimens.DRIVE_NUMBER, bold = true).apply { letterSpacing = 0.2f }, Dimens.SPACE_2)
                        text.addSpaced(label("다른 차에서 더 많은 코스의 '코드로 받기'에 넣으면 받습니다", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_2)
                    }
                    text.addView(Space(context), fill())
                    text.addSpaced(primaryButton("확인", onDone), Dimens.SPACE_6)
                }.onFailure { problem.text = if (it is ShareError.Http) it.message.orEmpty() else OFFLINE_NOTE }
            }
        }
    }
    buttons.addView(agree, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_4) })
    buttons.addView(quietButton("취소", onCancel), LinearLayout.LayoutParams(0, MATCH, 1f))
    text.addSpaced(buttons, Dimens.SPACE_6)

    val drawing = CoursePathView(this).apply {
        asCard()
        show(course, emptyList())
    }
    val edge = dp(kit.edge)
    panes(text, drawing, kit.wide).apply {
        setBackgroundColor(palette.bg)
        setPadding(edge, edge, edge, edge)
    }
}
