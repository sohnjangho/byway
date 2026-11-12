package com.byway.ui.share

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.byway.core.course.Course
import com.byway.core.course.parseCourse
import com.byway.core.share.SharedCourse
import com.byway.core.share.isCode
import com.byway.service.CourseCatalog
import com.byway.service.CourseStore
import com.byway.service.Share
import com.byway.service.ShareError
import com.byway.service.ShareSync
import com.byway.ui.Dimens
import com.byway.ui.WRAP
import com.byway.ui.addSpaced
import com.byway.ui.column
import com.byway.ui.dp
import com.byway.ui.label
import com.byway.ui.palette
import com.byway.ui.row
import com.byway.ui.smallButton
import com.byway.ui.textField

/** 공유 코스 id 는 s- 로 시작한다(서버가 붙인다). */
internal fun isShared(id: String) = id.startsWith("s-")

/** 서버 목록과 코드로 받은 코스의 정보. 화면 한 번에 한 번만 읽는다. */
internal fun Context.sharedInfo(): Map<String, SharedCourse> =
    CourseStore.files(this).let { files -> (files.shared() + files.received()).associateBy { it.id } }

/**
 * 내가 만든 코스의 공유 상태와 버튼(공유하기 또는 공유 끄기, 지우기). 내 코스 카드 안에 들어간다.
 * 지우기는 두 번 눌러야 지운다. 공유 중이면 서버 사본부터 지운다.
 */
internal fun Context.madeCourseActions(course: Course, onShare: () -> Unit, onChanged: () -> Unit): View = column().apply {
    val context = this@madeCourseActions
    val uploadId = CourseStore.uploadOf(context, course.id)
    val info = uploadId?.let { id -> CourseStore.files(context).shared().firstOrNull { it.id == id } }
    val status = when {
        uploadId == null -> "공유하지 않은 코스"
        info == null -> "공유 중"
        else -> listOfNotNull("공유 중 · ${scopeTitle(info.visibility)}", info.code?.let { "코드 $it" }, finishesText(info.finishes)).joinToString(" · ")
    }
    val note = label(status, Dimens.LABEL, palette.inkSecondary)
    addView(note)
    val buttons = row()
    fun unshare(then: () -> Unit) {
        val id = uploadId ?: return then()
        note.text = "서버에서 지우는 중"
        Share.io.execute {
            val result = Share.client(context).delete(id)
            val gone = result.isSuccess || (result.exceptionOrNull() as? ShareError.Http)?.code == 404
            note.post {
                if (!gone) return@post run { note.text = OFFLINE_NOTE }
                CourseStore.setUpload(context, course.id, null)
                ShareSync.refresh(context, force = true) {}
                then()
            }
        }
    }
    val share = smallButton(if (uploadId == null) "공유하기" else "공유 끄기") { if (uploadId == null) onShare() else unshare(onChanged) }
    var armed = false
    lateinit var delete: Button
    delete = smallButton("지우기") {
        if (!armed) {
            armed = true
            delete.text = "다시 눌러 지우기"
            return@smallButton
        }
        unshare {
            CourseStore.files(context).deleteMine(course.id)
            CourseCatalog.invalidate()
            onChanged()
        }
    }
    buttons.addView(share, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
    buttons.addView(delete, LinearLayout.LayoutParams(0, WRAP, 1f))
    addSpaced(buttons, Dimens.SPACE_2)
}

/** 둘러보기 위쪽의 코드로 받기. 받으면 기기에 저장하고 그 코스를 홈에 올린다. */
internal fun Context.codeEntry(onReceived: (Course) -> Unit): View = column().apply {
    val context = this@codeEntry
    val field = textField("받은 코드 6자리", 6, code = true)
    val note = label("", Dimens.LABEL, palette.inkSecondary)
    val line = row().apply {
        addView(field, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
        addView(smallButton("코드로 받기") {
            val code = field.text.toString().trim().uppercase()
            if (!isCode(code)) return@smallButton run { note.text = "코드는 숫자와 영문 6자리입니다" }
            note.text = "받는 중"
            Share.io.execute {
                val result = Share.client(context).byCode(code)
                note.post {
                    result.onSuccess { got ->
                        val course = runCatching { parseCourse(got.body) }.getOrNull() ?: return@onSuccess run { note.text = "코스를 읽지 못했습니다" }
                        CourseStore.files(context).saveReceived(got)
                        CourseCatalog.invalidate()
                        onReceived(course)
                    }.onFailure { note.text = if ((it as? ShareError.Http)?.code == 404) "코드를 찾을 수 없습니다" else OFFLINE_NOTE }
                }
            }
        })
    }
    addView(line)
    addSpaced(note, Dimens.SPACE_2)
}

/** 홈 정보 칸의 공유 코스 줄. 남이 올린 코스면 신고 버튼이 있다. 신고 1건이면 서버가 목록에서 숨긴다. */
internal fun Context.sharedCourseRow(info: SharedCourse?, onReported: () -> Unit): View = row().apply {
    val context = this@sharedCourseRow
    val note = label(listOfNotNull("운전자가 공유한 코스", info?.let { finishesText(it.finishes) }).joinToString(" · "), Dimens.SMALL, palette.inkSecondary)
    addView(note, LinearLayout.LayoutParams(0, WRAP, 1f))
    if (info == null || info.mine) return@apply
    addView(smallButton("신고") {
        Share.io.execute {
            val result = Share.client(context).report(info.id)
            note.post {
                if (result.isFailure) return@post run { note.text = OFFLINE_NOTE }
                note.text = "신고했습니다. 확인 전까지 목록에서 숨깁니다"
                ShareSync.refresh(context, force = true, onChanged = onReported)
            }
        }
    })
}
