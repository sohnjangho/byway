package com.byway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.byway.FeatureRegistry
import com.byway.core.drive.CabinScene
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature
import com.byway.core.drive.DriveHost
import com.byway.core.drive.RouteFollower
import com.byway.core.engine.CourseEngine
import com.byway.feature.ambience.AmbienceFeature
import com.byway.feature.music.MusicFeature
import com.byway.platform.ConnectedMusic
import com.byway.platform.PleosCabin
import com.byway.platform.PleosLocation
import com.byway.platform.PleosNavi
import com.byway.platform.PleosVoice
import com.byway.platform.musicQuery

/** 이번 코스에 넣을 부가 기능. 차 안 연출 설정은 코스 시작 때 읽는다. */
internal fun courseFeatures(): List<DriveFeature> =
    FeatureRegistry.create()
        .filter { MyDrives.ambienceOn || it !is AmbienceFeature }
        .filter { MyDrives.musicOn || it !is MusicFeature }

/**
 * 코스 주행을 소유한다. 내비가 앞에 있어도 코스가 끊기지 않게 하는 곳이다.
 * 달리는 자리를 저장해 두어 앱이 꺼졌다 켜져도 이어갈 수 있고, 끝낼 때는 연출을 되돌린 뒤에 멈춘다.
 */
class DriveService : Service() {
    private lateinit var navi: PleosNavi
    private val music by lazy { ConnectedMusic(this) }

    /** 코스를 따라갈 쪽. 자율주행 구현이 생기면 여기서 고른다(제작가이드 14장). */
    private val route: RouteFollower get() = navi
    private lateinit var voice: PleosVoice
    private lateinit var cabin: PleosCabin
    private lateinit var location: PleosLocation
    private val main = Handler(Looper.getMainLooper())
    private var host: DriveHost? = null
    private var engine: CourseEngine? = null
    private var parked = DriveBoard.state.parked

    /** 마지막으로 저장한 것. 같은 값을 다시 쓰지 않는다. */
    private var saved: SavedRun? = null
    private var cabinMemory = ""

    /** 마지막으로 경로를 올린 때. 그 직후의 음성은 내비 안내 뒤로 미룬다. */
    private var routedAt: Long? = null
    private var chimed = false

    private val boardListener: (DriveState) -> Unit = { if (it.parked != parked) sendParked(it.parked) }
    private val stopLater = Runnable { stopSelf() }
    private val watch = object : Runnable {
        override fun run() {
            watchLocation()
            main.postDelayed(this, WATCH_MS)
        }
    }

    private val boardFeature = object : DriveFeature {
        override val id = "board"
        override fun onEvent(event: DriveEvent): List<DriveCommand> {
            if (event is DriveEvent.CourseFinished) {
                DriveBoard.update { it.copy(finished = it.course, prompt = null, stale = false) }
                forget()
                main.postDelayed(stopLater, FINISH_DELAY_MS)
            }
            return emptyList()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        navi = PleosNavi(this) { dispatch(it) }
        voice = PleosVoice(this).also { it.start() }
        cabin = PleosCabin(this) { memory ->
            cabinMemory = memory
            save()
        }.also { it.start() }
        location = PleosLocation(this) { point ->
            DriveBoard.update { it.copy(position = point) }
            dispatch(DriveEvent.LocationUpdated(point))
        }
        navi.connect { Log.w(TAG, "navi connect failed", it) }
        location.start()
        DriveBoard.listen(boardListener)
        main.postDelayed(watch, WATCH_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCourse(requireNotNull(intent.getStringExtra(EXTRA_COURSE_ID)), intent.getIntExtra(EXTRA_RESUME_FROM, 0))
            ACTION_STOP -> endCourse()
            ACTION_ANSWER -> dispatch(DriveEvent.Answered(intent.getBooleanExtra(EXTRA_YES, false)))
            ACTION_DISCARD -> discard()
        }
        return START_NOT_STICKY
    }

    private fun startCourse(courseId: String, resumeFrom: Int) {
        // 방금 끝낸 코스의 예약된 종료가 새 코스를 멈추지 않게 한다.
        main.removeCallbacks(stopLater)
        val course = CourseCatalog.find(this, courseId)
        val started = CourseEngine(ability = route.ability)
        engine = started
        host = DriveHost(started, courseFeatures() + boardFeature + FinishRecorder(this)) { id, failure ->
            Log.e(TAG, "feature off: $id", failure)
        }
        // 지난번에 되돌리지 못한 연출이 있으면 그때의 처음 값을 이어받는다.
        MyDrives.savedRun(this)?.cabin?.takeIf { it.isNotEmpty() }?.let {
            cabin.remember(it)
            cabinMemory = it
        }
        saved = null
        DriveBoard.update {
            it.copy(
                running = true, finished = null, course = course, line = course.name, spoken = "", sectionIndex = 0,
                remainingMeters = null, music = "", cabin = "", prompt = null, stale = false,
            )
        }
        sendParked(DriveBoard.state.parked)
        dispatch(DriveEvent.CourseStarted(course, resumeFrom))
    }

    /** 운전자가 코스를 끝냈다. 연출을 되돌리는 호출이 나갈 시간을 두고 멈춘다. */
    private fun endCourse() {
        if (host == null) {
            stopSelf()
            return
        }
        dispatch(DriveEvent.CourseStopped)
        host = null
        engine = null
        forget()
        DriveBoard.update { it.copy(running = false, prompt = null, stale = false) }
        main.postDelayed(stopLater, STOP_DELAY_MS)
    }

    /** 달리다 만 코스를 이어가지 않기로 했다. 그때 바꿔 둔 차 안을 되돌리고 기록을 지운다. */
    private fun discard() {
        MyDrives.savedRun(this)?.cabin?.let(cabin::remember)
        cabin.restore()
        forget()
        if (host == null) main.postDelayed(stopLater, STOP_DELAY_MS)
    }

    private fun forget() {
        saved = null
        cabinMemory = ""
        MyDrives.clearRun(this)
    }

    private fun sendParked(value: Boolean) {
        parked = value
        Log.i(TAG, "parked $value")
        dispatch(DriveEvent.DrivingStateChanged(value))
    }

    private fun dispatch(event: DriveEvent) {
        chimed = false
        host?.dispatch(event)?.forEach(::execute)
        save()
    }

    /** 경로를 바꾼 자리가 달라졌거나 연출이 차 안을 바꿨으면 남긴다. 위치마다 쓰지 않는다. */
    private fun save() {
        val snapshot = engine?.snapshot() ?: return
        val run = SavedRun(snapshot.courseId, snapshot.progress, cabinMemory)
        if (run == saved) return
        saved = run
        MyDrives.saveRun(this, run)
    }

    private fun execute(command: DriveCommand) {
        when (command) {
            is DriveCommand.FollowRoute -> {
                routedAt = SystemClock.elapsedRealtime()
                route.follow(command)
            }
            is DriveCommand.Say -> {
                when {
                    MyDrives.voiceOn -> voice.say(command.text, speechDelayMs(routedAt?.let { SystemClock.elapsedRealtime() - it }))
                    // 음성을 꺼 두어도 이탈·복귀·경로 교체는 소리 하나로 알린다. 한 번에 여러 문구가 나와도 한 번만 낸다.
                    command.alert && !chimed -> {
                        chimed = true
                        voice.chime()
                    }
                }
                DriveBoard.update { it.copy(line = command.text, spoken = command.text) }
            }
            is DriveCommand.SetCabin -> {
                cabin.apply(command.scene)
                DriveBoard.update { it.copy(cabin = describe(command.scene)) }
            }
            DriveCommand.RestoreCabin -> {
                cabin.restore()
                DriveBoard.update { it.copy(cabin = "") }
            }
            is DriveCommand.Ask -> DriveBoard.update { it.copy(prompt = command.prompt) }
            is DriveCommand.ShowLine -> DriveBoard.update { it.copy(line = command.text) }
            is DriveCommand.PlayMusic -> {
                music.play(musicQuery(command.track))
                DriveBoard.update { it.copy(music = command.track) }
            }
            DriveCommand.StopMusic -> {
                music.pause()
                DriveBoard.update { it.copy(music = "") }
            }
            is DriveCommand.ShowProgress -> DriveBoard.update {
                it.copy(sectionIndex = command.sectionIndex, remainingMeters = command.remainingMeters)
            }
            is DriveCommand.ShowPanel, is DriveCommand.Publish -> Log.w(TAG, "unhandled $command")
        }
    }

    /** 달리는 중에 위치의 시각이 오래 멈추면 화면에 알린다. 엔진은 시각을 모르므로 여기서 본다. */
    private fun watchLocation() {
        if (host == null) return
        val age = location.fixAgeMs() ?: return
        val stale = isLocationStale(age, parked)
        if (stale != DriveBoard.state.stale) DriveBoard.update { it.copy(stale = stale) }
    }

    private fun describe(scene: CabinScene) =
        "창문 ${scene.windowPosition ?: "-"} · 풍량 ${scene.fanSpeed ?: "-"} · 조명 ${scene.ambientBrightness ?: "-"}"

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        DriveBoard.stopListening(boardListener)
        location.release()
        cabin.release()
        navi.release()
        music.release()
        voice.release()
        DriveBoard.update { it.copy(running = false, prompt = null, stale = false) }
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "코스 주행", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("에움길")
            .setContentText("코스를 달리는 중입니다")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .build()
    }

    companion object {
        private const val TAG = "DRIVE"
        private const val CHANNEL = "drive"
        private const val NOTIFICATION_ID = 1
        private const val FINISH_DELAY_MS = 6_000L

        /** 연출을 되돌리는 호출(읽고, 견주고, 쓰기)이 나갈 시간. */
        private const val STOP_DELAY_MS = 2_000L
        private const val WATCH_MS = 2_000L
        private const val ACTION_START = "com.byway.START"
        private const val ACTION_STOP = "com.byway.STOP"
        private const val ACTION_ANSWER = "com.byway.ANSWER"
        private const val ACTION_DISCARD = "com.byway.DISCARD"
        private const val EXTRA_COURSE_ID = "courseId"
        private const val EXTRA_RESUME_FROM = "resumeFrom"
        private const val EXTRA_YES = "yes"

        /** [resumeFrom]은 이어 달릴 때 코스 위의 지점 번호다. 처음부터면 0 이다. */
        fun start(context: Context, courseId: String, resumeFrom: Int = 0) {
            context.startForegroundService(
                Intent(context, DriveService::class.java).setAction(ACTION_START)
                    .putExtra(EXTRA_COURSE_ID, courseId).putExtra(EXTRA_RESUME_FROM, resumeFrom),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DriveService::class.java).setAction(ACTION_STOP))
        }

        /** 주행 화면의 답 버튼. */
        fun answer(context: Context, yes: Boolean) {
            context.startService(Intent(context, DriveService::class.java).setAction(ACTION_ANSWER).putExtra(EXTRA_YES, yes))
        }

        /** 달리다 만 코스를 그만둔다. 되돌릴 연출이 없으면 기록만 지운다. */
        fun discard(context: Context) {
            if (MyDrives.savedRun(context)?.cabin.isNullOrEmpty()) {
                MyDrives.clearRun(context)
            } else {
                context.startForegroundService(Intent(context, DriveService::class.java).setAction(ACTION_DISCARD))
            }
        }
    }
}
