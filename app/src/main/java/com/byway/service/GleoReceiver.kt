package com.byway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.byway.core.gleo.GleoEffect
import com.byway.core.gleo.GleoMode
import com.byway.core.gleo.GleoNames
import com.byway.core.gleo.GleoOutcome
import com.byway.core.gleo.GleoReply
import com.byway.core.gleo.GleoStatus
import com.byway.core.gleo.GleoWorld
import com.byway.core.gleo.decideGleo
import com.byway.core.gleo.parseGleoParams
import com.byway.platform.PleosGleo
import com.byway.ui.CourseBook
import com.byway.ui.moodWords
import com.byway.ui.searchWords
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gleo 가 부르는 함수 세 개를 받는다(Gleo 음성 연동 설계). 화면은 띄우지 않는다.
 * 판단은 core 의 [decideGleo]가 하고, 여기서는 지금 상태를 모아 넘긴 뒤 시킨 일을 화면 버튼과 같은 입구로 실행한다.
 * Gleo 는 7초 뒤 취소하므로 6초 안에 꼭 한 번 답한다. 늦게 끝난 판단은 실행하지 않고 버린다.
 */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val function = intent.action?.takeIf { it in GleoNames.ALL } ?: return
        val raw = intent.getStringExtra(GleoNames.PARAM_EXTRA)
        val app = context.applicationContext
        val pending = goAsync()
        val once = ReplyOnce { PleosGleo.respond(app, it) }
        val finished = AtomicBoolean(false)
        fun finish() {
            if (finished.compareAndSet(false, true)) pending.finish()
        }
        val timeout = Runnable {
            once.reply(GleoReply(function, GleoStatus.FAIL, TIMEOUT_MESSAGE))
            finish()
        }
        main.postDelayed(timeout, TIMEOUT_MS)
        worker.execute {
            val outcome = runCatching { decideGleo(function, parseGleoParams(raw), world(app)) }
                .getOrElse {
                    Log.e(TAG, "decide failed", it)
                    GleoOutcome(GleoReply(function, GleoStatus.FAIL, ERROR_MESSAGE))
                }
                .let { if (!once.sent) withSharing(app, it) else it }
            main.post {
                main.removeCallbacks(timeout)
                if (!once.sent) once.reply(run(app, outcome))
                finish()
            }
        }
    }

    /** 위치 공유 끄기는 서버에 묻는 일이라 배경 스레드에서 결과까지 받는다. */
    private fun withSharing(context: Context, outcome: GleoOutcome): GleoOutcome {
        val effect = outcome.effect as? GleoEffect.StopSharing ?: return outcome
        val off = Share.available && Share.client(context).sharing(effect.code, false).isSuccess
        return if (off) outcome.copy(effect = GleoEffect.None)
        else GleoOutcome(outcome.reply.copy(status = GleoStatus.FAIL, message = "The server could not be reached to stop location sharing."))
    }

    /** 판단이 시킨 일을 실행하고 실제 답을 돌려준다. 화면의 같은 버튼이 부르는 입구를 그대로 쓴다. */
    private fun run(context: Context, outcome: GleoOutcome): GleoReply {
        when (val effect = outcome.effect) {
            GleoEffect.None, is GleoEffect.StopSharing -> Unit
            is GleoEffect.Remember -> GleoMemory.remember(effect.ids, SystemClock.elapsedRealtime())
            is GleoEffect.Start -> try {
                DriveService.start(context, effect.courseId, effect.resumeFrom)
            } catch (e: IllegalStateException) {
                // 안드로이드 12+: 앱이 화면 뒤에 있고 포그라운드 서비스가 없으면 시작이 막힌다
                // (ForegroundServiceStartNotAllowedException 은 IllegalStateException 이다, 설계 3-2).
                Log.w(TAG, "start refused", e)
                return outcome.reply.copy(status = GleoStatus.FAIL, message = OPEN_APP_MESSAGE, data = emptyList(), mode = GleoMode.VOICE)
            }
            is GleoEffect.Answer -> DriveService.answer(context, effect.yes)
            GleoEffect.EndCourse -> DriveService.stop(context)
            is GleoEffect.SetVoice -> MyDrives.voiceOn = effect.on
            is GleoEffect.SetCabin -> MyDrives.ambienceOn = effect.on
            GleoEffect.StopRecording -> DriveService.recordStop(context)
        }
        return outcome.reply
    }

    private fun world(context: Context): GleoWorld {
        val courses = CourseCatalog.all(context)
        val book = CourseBook(courses)
        val state = DriveBoard.state
        return GleoWorld(
            courses = courses,
            here = state.position ?: BrowseLocation.last,
            mine = CourseStore.files(context).mine().map { it.id }.toSet() + MyDrives.saved(context),
            running = state.course.takeIf { state.running },
            sectionIndex = state.sectionIndex,
            remainingMeters = state.remainingMeters,
            prompt = state.prompt,
            recording = state.recording != null,
            partyCode = MyParty.code(context),
            savedRun = MyDrives.savedRun(context)?.let { it.courseId to it.progress },
            lastResults = GleoMemory.recall(SystemClock.elapsedRealtime()),
            metersOf = { book.facts(it).meters },
            wordsOf = ::searchWords,
            moodNames = { moodWords(it.tags) },
        )
    }

    companion object {
        private const val TAG = "BywayGleo"
        private const val TIMEOUT_MS = 6_000L
        private const val TIMEOUT_MESSAGE = "A timeout occurred while processing the request."
        private const val ERROR_MESSAGE = "An unexpected internal error occurred."
        private const val OPEN_APP_MESSAGE = "Open 에움길 on the screen and try again."
        private val main = Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadExecutor()
    }
}

/** 요청 하나에 Gleo 로 가는 답은 한 번뿐이다. 6초 타이머와 판단 결과 중 먼저 온 것만 보낸다. */
internal class ReplyOnce(private val send: (GleoReply) -> Unit) {
    private val done = AtomicBoolean(false)

    val sent: Boolean get() = done.get()

    fun reply(reply: GleoReply): Boolean {
        if (!done.compareAndSet(false, true)) return false
        send(reply)
        return true
    }
}
