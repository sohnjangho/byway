package com.byway.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.byway.core.share.SharedCourse

/** 화면에 보이는 값(코스, 완주 횟수, 범위, 코드)이 같으면 같은 목록이다. 같으면 화면을 다시 만들지 않는다. */
fun sameShared(old: List<SharedCourse>, new: List<SharedCourse>): Boolean =
    old.sortedBy { it.id } == new.sortedBy { it.id }

/**
 * 공유 코스 목록을 서버에서 받아 기기에 둔다. 정차 화면을 열 때 부르고, 30초 안에 다시 부르면 건너뛴다.
 * 코드로 받은 코스가 서버에서 지워졌으면 기기에서도 지운다(공유 설계 6-4). 주행은 언제나 기기 파일로 한다.
 */
object ShareSync {
    private const val QUIET_MS = 30_000L
    private val main = Handler(Looper.getMainLooper())
    private var lastAt = 0L

    /** 마지막으로 서버에 닿지 못했다(서버 주소가 없는 제출 APK 는 처음부터). 둘러보기가 안내 문구를 보인다. */
    @Volatile var offline = !Share.available
        private set

    fun refresh(context: Context, force: Boolean = false, onChanged: () -> Unit) {
        if (!Share.available) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAt < QUIET_MS) return
        lastAt = now
        val app = context.applicationContext
        Share.io.execute {
            val client = Share.client(app)
            val files = CourseStore.files(app)
            val fresh = client.courses()
            offline = fresh.isFailure
            var changed = false
            fresh.onSuccess { list ->
                if (!sameShared(files.shared(), list)) {
                    files.replaceShared(list)
                    changed = true
                }
            }
            if (!offline) {
                files.received().forEach { got ->
                    val gone = got.code == null || (client.byCode(got.code!!).exceptionOrNull() as? ShareError.Http)?.code == 404
                    if (gone) {
                        files.deleteReceived(got.id)
                        changed = true
                    }
                }
            }
            if (changed) main.post {
                CourseCatalog.invalidate()
                onChanged()
            }
        }
    }
}
