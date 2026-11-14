package com.byway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.byway.core.gleo.gleoParamsFrom
import com.byway.core.geo.GeoPoint
import com.byway.platform.PleosGleo
import com.byway.ui.CourseBook
import com.byway.ui.moodWords
import com.byway.ui.searchWords
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gleo 가 부르는 함수 세 개를 받는다(Gleo 음성 연동 설계). 화면은 띄우지 않는다.
 * 판단은 core 의 [decideGleo]가 하고, 여기서는 지금 상태를 모아 넘긴 뒤 시킨 일을 화면 버튼과 같은 입구로 실행한다.
 * Gleo 는 7초 뒤 취소하므로 5초 안에 꼭 한 번 답한다. 늦게 끝난 판단은 실행하지 않고 버린다.
 */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in GleoNames.ALL) return
        val pending = goAsync()
        handle(context, intent) { pending.finish() }
    }

    /** 방송([GleoReceiver])과 출발 화면([GleoStartActivity])이 함께 쓰는 본문. 답을 보낸 뒤 [done]을 한 번 부른다. */
    internal fun handle(context: Context, intent: Intent, done: () -> Unit) {
        val function = intent.action?.takeIf { it in GleoNames.ALL } ?: return done()
        val raw = intent.getStringExtra(GleoNames.PARAM_EXTRA)
        @Suppress("DEPRECATION")
        val extras = intent.extras?.let { bundle -> bundle.keySet().associateWith { bundle.get(it) } }.orEmpty()
        val app = context.applicationContext
        val once = ReplyOnce { PleosGleo.respond(app, it) }
        val finished = AtomicBoolean(false)
        fun finish() {
            if (finished.compareAndSet(false, true)) done()
        }
        val timeout = Runnable {
            once.reply(GleoReply(function, GleoStatus.FAIL, TIMEOUT_MESSAGE))
            finish()
        }
        main.postDelayed(timeout, TIMEOUT_MS)
        worker.execute {
            val outcome = runCatching { decideGleo(function, gleoParamsFrom(raw, extras), world(app)) }
                .getOrElse {
                    Log.e(TAG, "decide failed", it)
                    GleoOutcome(GleoReply(function, GleoStatus.FAIL, ERROR_MESSAGE))
                }
                .let { if (!once.sent) withSharing(app, it) else it }
            main.post {
                main.removeCallbacks(timeout)
                if (!once.sent) once.reply(safely(function) { run(app, outcome) })
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
            here = voicePosition(
                drive = state.position,
                driveLive = state.running || state.recording != null,
                browse = BrowseLocation.last,
                browseAgeMs = BrowseLocation.lastAt?.let { SystemClock.elapsedRealtime() - it },
            ),
            mine = CourseStore.files(context).mine().map { it.id }.toSet() + MyDrives.saved(context),
            running = state.course.takeIf { state.running },
            sectionIndex = state.sectionIndex,
            remainingMeters = state.remainingMeters,
            prompt = state.prompt,
            recording = state.recording != null,
            partyCode = MyParty.code(context),
            savedRun = MyDrives.savedRun(context)?.let { it.courseId to it.progress },
            lastResults = GleoMemory.recall(SystemClock.elapsedRealtime()),
            locationGranted = context.checkSelfPermission(LOCATION) == PackageManager.PERMISSION_GRANTED,
            metersOf = { book.facts(it).meters },
            wordsOf = ::searchWords,
            moodNames = { moodWords(it.tags) },
        )
    }

    companion object {
        private const val TAG = "BywayGleo"
        /** Gleo 의 7초는 Gleo 가 보낸 때부터라 프로세스 시작 시간을 빼고 5초로 둔다(검토 I6). */
        private const val TIMEOUT_MS = 5_000L
        private const val LOCATION = "pleos.car.permission.FUSED_LOCATION"
        private const val TIMEOUT_MESSAGE = "A timeout occurred while processing the request."
        private const val ERROR_MESSAGE = "An unexpected internal error occurred."
        private const val OPEN_APP_MESSAGE = "Open 에움길 on the screen and try again."
        private val main = Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadExecutor()
    }
}

/** 둘러보기 위치를 믿는 시간. 그보다 오래된 자리로 "근처"를 계산하지 않는다(검토 I4). */
private const val BROWSE_FRESH_MS = 120_000L

/**
 * 음성 찾기가 쓸 차 위치. 주행 서비스가 돌고 있으면 그 위치를, 아니면 2분 안에 받은 둘러보기 위치만 쓴다.
 * 모르면 null 이고 판단이 "위치를 몰라 거리 조건을 건너뛰었다"고 답한다.
 */
internal fun voicePosition(drive: GeoPoint?, driveLive: Boolean, browse: GeoPoint?, browseAgeMs: Long?): GeoPoint? = when {
    driveLive && drive != null -> drive
    browse != null && browseAgeMs != null && browseAgeMs <= BROWSE_FRESH_MS -> browse
    else -> null
}

/** 시킨 일을 실행하다 예외가 나도 앱이 죽지 않고 실패로 답한다(검토 M2). */
internal fun safely(
    function: String,
    report: (RuntimeException) -> Unit = { android.util.Log.e("BywayGleo", "effect failed", it) },
    block: () -> GleoReply,
): GleoReply = try {
    block()
} catch (failure: RuntimeException) {
    report(failure)
    GleoReply(function, GleoStatus.FAIL, "An unexpected internal error occurred.")
}

/** 요청 하나에 Gleo 로 가는 답은 한 번뿐이다. 5초 타이머와 판단 결과 중 먼저 온 것만 보낸다. */
internal class ReplyOnce(private val send: (GleoReply) -> Unit) {
    private val done = AtomicBoolean(false)

    val sent: Boolean get() = done.get()

    fun reply(reply: GleoReply): Boolean {
        if (!done.compareAndSet(false, true)) return false
        send(reply)
        return true
    }
}
