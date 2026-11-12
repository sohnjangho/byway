package com.byway.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.byway.core.share.PartyView
import com.byway.core.share.needsAnswer

/** 리더가 고른 코스를 물으려고 파티를 받는 때. 정차 중이고, 코스가 달리지 않고, 파티에 들어가 있고, 서버가 있을 때뿐이다(함께달리기 설계 6-6, 6-10.1). */
fun shouldWatch(parked: Boolean, running: Boolean, partyCode: String?, online: Boolean): Boolean =
    parked && !running && partyCode != null && online

/**
 * 정차 중에 파티를 5초마다 받아, 리더가 코스를 골랐는지 본다. 함께달리기 탭 밖에서도 질문을 띄우려는 것이다.
 * 물을지가 바뀌거나 새 제안이 오면 [onChanged]를 부른다. 파티가 끝났거나 내가 빠졌으면(404) 파티를 비운다.
 */
object PartyWatch {
    private const val WATCH_MS = 5_000L
    private val main = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var onChanged: (() -> Unit)? = null
    private var busy = false

    /** 마지막으로 받은 파티. 정차 화면이 이것으로 질문을 띄운다. */
    var view: PartyView? = null
        private set

    private val loop = object : Runnable {
        override fun run() {
            val app = context ?: return
            main.postDelayed(this, WATCH_MS)
            val code = MyParty.code(app) ?: return clear()
            if (busy) return
            busy = true
            Share.io.execute {
                val tick = partyTick(null, sharing = false, send = { Result.success(Unit) }, fetch = { Share.client(app).party(code) })
                main.post {
                    busy = false
                    when (tick) {
                        is PartyTick.Seen -> {
                            val before = view
                            view = tick.view
                            if (asking(before) != asking(tick.view) || before?.proposal != tick.view.proposal) onChanged?.invoke()
                        }
                        PartyTick.Ended -> {
                            MyParty.set(app, null)
                            clear()
                        }
                        PartyTick.Unreachable -> Unit
                    }
                }
            }
        }
    }

    private fun asking(view: PartyView?): Boolean = view?.let(::needsAnswer) == true

    private fun clear() {
        if (view == null) return
        view = null
        onChanged?.invoke()
    }

    /** [on]이면 받기 시작하고(이미 받는 중이면 그대로), 아니면 멈춘다. */
    fun keep(context: Context, on: Boolean, onChanged: () -> Unit) {
        if (!on) return stop()
        this.onChanged = onChanged
        if (this.context != null) return
        this.context = context.applicationContext
        main.post(loop)
    }

    fun stop() {
        main.removeCallbacks(loop)
        context = null
        onChanged = null
    }

    /** 바로 한 번 더 받는다. 옛 제안에 답해 409를 받았을 때 새 제안을 묻는다. */
    fun poke() {
        if (context == null) return
        main.removeCallbacks(loop)
        main.post(loop)
    }

    /** 승낙한 뒤에는 다시 묻지 않게 내 줄을 승낙으로 둔다. 다음에 받으면 서버 값으로 바뀐다. */
    fun accepted() {
        view = view?.let { v -> v.copy(members = v.members.map { if (it.me) it.copy(accepted = true) else it }) }
    }

    /** 거부해 파티를 나왔다. */
    fun forget() {
        view = null
    }
}
