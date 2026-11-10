package com.byway.ui.party

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.byway.core.course.Course
import com.byway.core.geo.GeoPoint
import com.byway.core.record.nameProblem
import com.byway.core.share.AVATARS
import com.byway.core.share.NICKNAME_MAX
import com.byway.core.share.NewParty
import com.byway.core.share.PartyView
import com.byway.core.share.Profile
import com.byway.core.share.Visibility
import com.byway.core.share.isCode
import com.byway.service.CourseCatalog
import com.byway.service.CourseStore
import com.byway.service.MyParty
import com.byway.service.MyProfile
import com.byway.service.PartyTick
import com.byway.service.Share
import com.byway.service.ShareError
import com.byway.service.ShareSync
import com.byway.service.livePins
import com.byway.service.partyTick
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
import com.byway.ui.row
import com.byway.ui.smallButton
import com.byway.ui.MoodLayer
import com.byway.ui.share.OFFLINE_NOTE
import com.byway.ui.textField

private const val POLL_MS = 2_000L

/**
 * 드라이빙 파티 화면(공유 설계 6-5). 정차 중에만 열린다.
 * 파티가 없으면 프로필·만들기·코드로 참여·즐겨찾는 파티, 있으면 코드·멤버·위치 공유·별표·내비 입히기·끝내기를 보인다.
 * 파티 안에서는 2초마다 멤버와 위치를 새로 받아 오른쪽 코스 그림에 핀으로 찍는다.
 */
internal fun partyScreen(context: Context, kit: BrowseKit, onStart: (String) -> Unit, onClose: () -> Unit): View = with(context) {
    // 파티 안에서는 파티 코스의 분위기를 깐다(화면 다듬기 설계 4절). 파티 밖은 바탕색이다.
    val mood = MoodLayer(context, kit.window)
    val text = column()
    val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(text)
    }
    val drawing = CoursePathView(this)
    val actions = LinearLayout(context)
    val note = label("", Dimens.SMALL, palette.accent)
    var view: PartyView? = null
    lateinit var render: () -> Unit

    /** 파티 코스. 내가 올린 공유본이면 기기에는 내 코스(m-)로 있다. */
    fun localCourse(id: String): Course? = CourseCatalog.all(context).firstOrNull { it.id == id }
        ?: CourseStore.files(context).mine().firstOrNull { CourseStore.uploadOf(context, it.id) == id }

    fun call(work: () -> Result<*>, then: (Result<*>) -> Unit) {
        note.text = "서버와 주고받는 중"
        Share.io.execute {
            val result = work()
            text.post {
                note.text = result.exceptionOrNull()?.let { if (it is ShareError.Http) it.message.orEmpty() else OFFLINE_NOTE }.orEmpty()
                then(result)
            }
        }
    }

    var polling = false
    val poll = object : Runnable {
        override fun run() {
            val code = MyParty.code(context) ?: return
            text.postDelayed(this, POLL_MS)
            // 앞 요청이 아직 돌면 건너뛴다. 서버가 늦어도 요청이 쌓여 철회·나가기가 밀리지 않게 한다.
            if (polling) return
            polling = true
            Share.io.execute {
                val tick = partyTick(null, sharing = false, send = { Result.success(Unit) }, fetch = { Share.client(context).party(code) })
                text.post {
                    polling = false
                    when (tick) {
                        is PartyTick.Seen -> {
                            val first = view == null || view?.members != tick.view.members || view?.open != tick.view.open
                            view = tick.view
                            if (first) render() else showPins(context, drawing, tick.view)
                        }
                        PartyTick.Ended -> {
                            MyParty.set(context, null)
                            view = null
                            render()
                        }
                        PartyTick.Unreachable -> note.text = OFFLINE_NOTE
                    }
                }
            }
        }
    }

    /** 바꾼 뒤 바로 다시 받는다. 폴링이 두 겹으로 돌지 않게 먼저 걷어 낸다. */
    fun refreshNow() {
        view = null
        text.removeCallbacks(poll)
        text.post(poll)
    }

    fun outside() {
        mood.show(null)
        text.addView(label("드라이빙 파티", Dimens.HEADING, bold = true))
        text.addSpaced(label(PARTY_INTRO, Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_2)
        text.addSpaced(profileBlock(context) { render() }, Dimens.SPACE_6)
        val profile = MyProfile.get(context)
        val hidden = CourseStore.files(context).shared().filter { it.visibility == Visibility.PARTY && !it.mine }.map { it.id }.toSet()
        val all = CourseCatalog.all(context)
        val uploads = all.filter { it.id.startsWith("m-") }.mapNotNull { course -> CourseStore.uploadOf(context, course.id)?.let { course.id to it } }.toMap()
        val choices = partyCourseChoices(all, hidden, uploads)
        var picked = choices.firstOrNull()?.first
        picked?.let { id -> localCourse(id)?.let { drawing.show(it, emptyList()) } }
        text.addSpaced(label("파티 만들기", Dimens.SMALL, bold = true), Dimens.SPACE_6)
        text.addSpaced(label("내가 만든 코스는 먼저 공유하면 고를 수 있습니다", Dimens.LABEL, palette.inkSecondary), Dimens.SPACE_2)
        text.addSpaced(chipRow(choices.map { Choice(it.first, it.second) }, picked, Dimens.SMALL) { id ->
            picked = id
            localCourse(id)?.let { drawing.show(it, emptyList()) }
        }, Dimens.SPACE_2)
        PARTY_JOIN_CONSENT.forEach { text.addSpaced(label("· $it", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_2) }
        text.addSpaced(primaryButton(CREATE_BUTTON) {
            val me = profile ?: return@primaryButton run { note.text = "먼저 닉네임과 아바타를 정해 주세요" }
            val courseId = picked ?: return@primaryButton
            call({ Share.client(context).createParty(NewParty(courseId, "", me)) }) { result ->
                (result.getOrNull() as? PartyView)?.let {
                    MyParty.set(context, it.code)
                    refreshNow()
                }
            }
        }, Dimens.SPACE_3)
        text.addSpaced(label("코드로 참여", Dimens.SMALL, bold = true), Dimens.SPACE_6)
        val field = textField("파티 코드 6자리", 6, code = true)
        text.addSpaced(row().apply {
            addView(field, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
            addView(smallButton(JOIN_BUTTON) {
                val me = profile ?: return@smallButton run { note.text = "먼저 닉네임과 아바타를 정해 주세요" }
                val code = field.text.toString().trim().uppercase()
                if (!isCode(code)) return@smallButton run { note.text = "코드는 숫자와 영문 6자리입니다" }
                call({ Share.client(context).join(code, me) }) { result ->
                    (result.getOrNull() as? PartyView)?.let {
                        MyParty.set(context, it.code)
                        // 파티 코스가 기기에 없으면 서버 목록에서 함께 받는다.
                        ShareSync.refresh(context, force = true) { refreshNow() }
                        refreshNow()
                    }
                }
            })
        }, Dimens.SPACE_2)
        val favorites = column()
        text.addSpaced(favorites, Dimens.SPACE_6)
        Share.io.execute {
            val list = Share.client(context).parties().getOrNull().orEmpty().filter { !it.open }
            favorites.post {
                if (list.isEmpty()) return@post
                favorites.addView(label("즐겨찾는 파티", Dimens.SMALL, bold = true))
                list.forEach { party ->
                    favorites.addSpaced(row().apply {
                        addView(label(party.name, Dimens.BODY), LinearLayout.LayoutParams(0, WRAP, 1f))
                        addView(smallButton("이 코스로 다시 열기") {
                            val courseId = picked ?: return@smallButton
                            call({ Share.client(context).reopen(party.code, courseId) }) { result ->
                                (result.getOrNull() as? PartyView)?.let {
                                    MyParty.set(context, it.code)
                                    refreshNow()
                                }
                            }
                        })
                    }, Dimens.SPACE_2)
                }
            }
        }
    }

    fun inside(party: PartyView) {
        val me = party.members.firstOrNull { it.me }
        val course = localCourse(party.courseId)
        mood.show(course)
        course?.let { drawing.show(it, emptyList()) }
        showPins(context, drawing, party)
        text.addView(label("드라이빙 파티", Dimens.SMALL, palette.inkSecondary))
        text.addSpaced(label(party.name, Dimens.HEADING, bold = true), Dimens.SPACE_2)
        text.addSpaced(label("파티 코드", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_4)
        text.addView(label(party.code, Dimens.DRIVE_NUMBER, bold = true).apply { letterSpacing = 0.2f })
        text.addSpaced(label(course?.name?.let { "코스 · $it" } ?: "코스를 받는 중입니다", Dimens.BODY), Dimens.SPACE_2)
        text.addSpaced(label("멤버 ${party.members.size}명", Dimens.SMALL, bold = true), Dimens.SPACE_6)
        party.members.forEach { member ->
            text.addSpaced(row().apply {
                addView(AvatarDot(context, member.avatar, chosen = false), LinearLayout.LayoutParams(dp(Dimens.TOUCH_MIN), dp(Dimens.TOUCH_MIN)))
                addView(column().apply {
                    addView(label(member.nickname, Dimens.BODY, bold = true))
                    memberTags(member).takeIf { it.isNotEmpty() }?.let { addView(label(it, Dimens.LABEL, palette.inkSecondary)) }
                }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(Dimens.SPACE_3) })
            }, Dimens.SPACE_2)
        }
        val code = party.code
        if (me?.sharing == true) {
            text.addSpaced(quietButton("위치 공유 끄기") {
                call({ Share.client(context).sharing(code, false) }) { if (it.isSuccess) refreshNow() }
            }, Dimens.SPACE_6)
        } else {
            text.addSpaced(label("위치 공유", Dimens.SMALL, bold = true), Dimens.SPACE_6)
            LOCATION_CONSENT.forEach { text.addSpaced(label("· $it", Dimens.SMALL, palette.inkSecondary), Dimens.SPACE_2) }
            text.addSpaced(primaryButton("동의하고 위치 공유") {
                call({ Share.client(context).sharing(code, true) }) { if (it.isSuccess) refreshNow() }
            }, Dimens.SPACE_3)
        }
        text.addSpaced(row().apply {
            addView(label(STAR_NOTE, Dimens.LABEL, palette.inkSecondary), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
            addView(smallButton(if (me?.starred == true) "즐겨찾기 끄기" else "즐겨찾기") {
                val on = me?.starred != true
                call({ Share.client(context).star(code, on) }) { if (it.isSuccess) refreshNow() }
            })
        }, Dimens.SPACE_6)
        val start = primaryButton("내비에 코스 입히기") { course?.let { onStart(it.id) } ?: run { note.text = "코스를 받는 중입니다" } }
        val leave = quietButton(if (party.owner) "파티 끝내기" else "나가기") {
            call({ if (party.owner) Share.client(context).end(code) else Share.client(context).leave(code) }) { result ->
                // 실패하면 파티를 그대로 둔다. 이미 없는 파티(404)는 끝난 것으로 본다.
                if (result.isFailure && (result.exceptionOrNull() as? ShareError.Http)?.code != 404) return@call
                MyParty.set(context, null)
                view = null
                render()
            }
        }
        actions.addView(start, LinearLayout.LayoutParams(0, WRAP, 1.4f).apply { marginEnd = dp(Dimens.SPACE_3) })
        actions.addView(leave, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
    }

    render = {
        text.removeAllViews()
        actions.removeAllViews()
        val party = view
        when {
            !Share.available -> {
                text.addView(label("드라이빙 파티", Dimens.HEADING, bold = true))
                text.addSpaced(label(OFFLINE_NOTE, Dimens.BODY, palette.inkSecondary), Dimens.SPACE_3)
            }
            MyParty.code(context) == null -> outside()
            party == null -> text.addView(label("파티를 불러오는 중", Dimens.BODY, palette.inkSecondary))
            else -> inside(party)
        }
        actions.addView(quietButton("닫기", onClose), LinearLayout.LayoutParams(0, MATCH, 0.8f))
    }
    render()

    val edge = dp(kit.edge)
    // 주요 버튼은 스크롤 밖 아래 칸에 둔다. 멤버와 동의 글이 길어져도 버튼이 밀려나지 않는다.
    val left = column().apply {
        addView(scroll, fill())
        addSpaced(note, Dimens.SPACE_2)
        addSpaced(actions, Dimens.SPACE_3)
    }
    val content = panes(left, drawing, kit.wide).apply {
        setPadding(edge, edge, edge, edge)
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                text.removeCallbacks(poll)
                text.post(poll)
            }

            override fun onViewDetachedFromWindow(v: View) {
                text.removeCallbacks(poll)
            }
        })
    }
    mood.hold(content)
}

/** 오른쪽 코스 그림에 다른 멤버의 핀을 찍는다. */
private fun showPins(context: Context, drawing: CoursePathView, party: PartyView) {
    drawing.setPins(livePins(party).mapNotNull { pin ->
        party.members.firstOrNull { it.id == pin.memberId }?.let { MapPin(pin.memberId, GeoPoint(pin.lat, pin.lon), it.avatar) }
    })
}

/** 닉네임과 아바타. 정해 두지 않았으면 고르는 칸을 보인다. 실명 대신 닉네임만 쓴다. */
private fun profileBlock(context: Context, onSaved: () -> Unit): View = with(context) {
    val saved = MyProfile.get(this)
    column().apply {
        if (saved != null) {
            addView(row().apply {
                addView(AvatarDot(context, saved.avatar, chosen = false), LinearLayout.LayoutParams(dp(Dimens.TOUCH_MIN), dp(Dimens.TOUCH_MIN)))
                addView(label(saved.nickname, Dimens.BODY, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(Dimens.SPACE_3) })
                addView(smallButton("바꾸기") {
                    MyProfile.set(context, null)
                    onSaved()
                })
            })
            return@apply
        }
        addView(label("파티에서 보일 닉네임과 아바타", Dimens.SMALL, bold = true))
        val name = textField("닉네임 (${NICKNAME_MAX}자 안)", NICKNAME_MAX)
        addSpaced(name, Dimens.SPACE_2)
        var avatar: String? = null
        val dots = AVATARS.map { AvatarDot(context, it, chosen = false) }
        val strip = row()
        dots.forEach { dot ->
            dot.setOnClickListener {
                avatar = dot.name
                dots.forEach { it.chosen = it == dot }
            }
            strip.addView(dot, LinearLayout.LayoutParams(dp(Dimens.TOUCH_MIN), dp(Dimens.TOUCH_MIN)))
        }
        addSpaced(android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }, Dimens.SPACE_2)
        val problem = label("", Dimens.SMALL, palette.accent)
        addSpaced(problem, Dimens.SPACE_2)
        addSpaced(smallButton("저장") {
            val nickname = name.text.toString().trim()
            val chosen = avatar
            problem.text = when {
                nickname.isEmpty() -> "닉네임을 넣어 주세요"
                chosen == null -> "아바타를 골라 주세요"
                else -> nameProblem(nickname).orEmpty()
            }
            if (problem.text.isNotEmpty() || chosen == null) return@smallButton
            MyProfile.set(context, Profile(nickname, chosen))
            onSaved()
        }, Dimens.SPACE_2)
    }
}

/** 원형으로 자른 아바타 하나. 고른 것은 주황 테두리를 두른다. */
@SuppressLint("ViewConstructor")
internal class AvatarDot(context: Context, val name: String, chosen: Boolean) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    var chosen = chosen
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) / 2f * 0.78f
        PartyPins.drawCircle(canvas, context, width / 2f, height / 2f, name, radius)
        if (!chosen) return
        ring.color = context.palette.accent
        ring.strokeWidth = radius * 0.14f
        canvas.drawCircle(width / 2f, height / 2f, radius + ring.strokeWidth, ring)
    }
}
