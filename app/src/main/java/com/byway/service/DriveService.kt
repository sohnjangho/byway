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
import android.util.Log
import com.byway.FeatureRegistry
import com.byway.core.drive.CabinScene
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature
import com.byway.core.drive.DriveHost
import com.byway.core.engine.CourseEngine
import com.byway.platform.PleosCabin
import com.byway.platform.PleosLocation
import com.byway.platform.PleosNavi
import com.byway.platform.PleosVoice

/** 코스 주행을 소유한다. 내비가 앞에 있어도 코스가 끊기지 않게 하는 곳이다. */
class DriveService : Service() {
    private lateinit var navi: PleosNavi
    private lateinit var voice: PleosVoice
    private lateinit var cabin: PleosCabin
    private lateinit var location: PleosLocation
    private var host: DriveHost? = null
    private var parked = DriveBoard.state.parked

    private val boardListener: (DriveState) -> Unit = { if (it.parked != parked) sendParked(it.parked) }

    private val boardFeature = object : DriveFeature {
        override val id = "board"
        override fun onEvent(event: DriveEvent): List<DriveCommand> {
            if (event is DriveEvent.CourseFinished) {
                DriveBoard.update { it.copy(finished = it.course) }
                finishSoon()
            }
            return emptyList()
        }
    }

    private fun finishSoon() {
        Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, FINISH_DELAY_MS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        navi = PleosNavi(this)
        voice = PleosVoice(this).also { it.start() }
        cabin = PleosCabin(this).also { it.start() }
        location = PleosLocation(this) { point ->
            DriveBoard.update { it.copy(position = point) }
            dispatch(DriveEvent.LocationUpdated(point))
        }
        navi.connect { Log.w(TAG, "navi connect failed", it) }
        location.start()
        DriveBoard.listen(boardListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCourse(requireNotNull(intent.getStringExtra(EXTRA_COURSE_ID)))
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCourse(courseId: String) {
        val course = CourseCatalog.find(this, courseId)
        host = DriveHost(CourseEngine(), FeatureRegistry.create() + boardFeature) { id, failure ->
            Log.e(TAG, "feature off: $id", failure)
        }
        DriveBoard.update { it.copy(running = true, finished = null, course = course, line = course.name, spoken = "", cabin = "") }
        sendParked(DriveBoard.state.parked)
        dispatch(DriveEvent.CourseStarted(course))
    }

    private fun sendParked(value: Boolean) {
        parked = value
        Log.i(TAG, "parked $value")
        dispatch(DriveEvent.DrivingStateChanged(value))
    }

    private fun dispatch(event: DriveEvent) {
        host?.dispatch(event)?.forEach(::execute)
    }

    private fun execute(command: DriveCommand) {
        when (command) {
            is DriveCommand.FollowRoute -> navi.show(command.leg)
            is DriveCommand.Say -> {
                voice.say(command.text)
                DriveBoard.update { it.copy(spoken = command.text) }
            }
            is DriveCommand.SetCabin -> {
                cabin.apply(command.scene)
                DriveBoard.update { it.copy(cabin = describe(command.scene)) }
            }
            is DriveCommand.ShowLine -> DriveBoard.update { it.copy(line = command.text) }
            is DriveCommand.ShowPanel, is DriveCommand.Publish -> Log.w(TAG, "unhandled $command")
        }
    }

    private fun describe(scene: CabinScene) =
        "창문 ${scene.windowPosition ?: "-"} · 풍량 ${scene.fanSpeed ?: "-"} · 조명 ${scene.ambientBrightness ?: "-"}"

    override fun onDestroy() {
        DriveBoard.stopListening(boardListener)
        location.release()
        cabin.release()
        navi.release()
        voice.release()
        DriveBoard.update { it.copy(running = false) }
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
        private const val ACTION_START = "com.byway.START"
        private const val ACTION_STOP = "com.byway.STOP"
        private const val EXTRA_COURSE_ID = "courseId"

        fun start(context: Context, courseId: String) {
            context.startForegroundService(
                Intent(context, DriveService::class.java).setAction(ACTION_START).putExtra(EXTRA_COURSE_ID, courseId),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DriveService::class.java))
        }
    }
}
