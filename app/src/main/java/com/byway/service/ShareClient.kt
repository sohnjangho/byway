package com.byway.service

import android.content.Context
import com.byway.BuildConfig
import com.byway.core.share.Answer
import com.byway.core.share.DeviceToken
import com.byway.core.share.MemberRef
import com.byway.core.share.NewParty
import com.byway.core.share.PartyView
import com.byway.core.share.Position
import com.byway.core.share.Problem
import com.byway.core.share.Profile
import com.byway.core.share.Propose
import com.byway.core.share.Reopen
import com.byway.core.share.SharedCourse
import com.byway.core.share.Toggle
import com.byway.core.share.UploadCourse
import com.byway.core.share.Uploaded
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 서버에 닿지 못한 이유. 화면은 이 셋만 구별한다. */
sealed class ShareError(message: String) : Exception(message) {
    /** 이 빌드에 서버 주소가 없다(제출 APK). */
    class Offline : ShareError("공유 서버에 연결할 수 없습니다")
    class Network(cause: IOException) : ShareError("공유 서버에 연결할 수 없습니다") {
        init { initCause(cause) }
    }
    class Http(val code: Int, message: String) : ShareError(message)
}

private const val TIMEOUT_SECONDS = 5L

fun shareHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(TIMEOUT_SECONDS + 1, TimeUnit.SECONDS)
    .build()

/**
 * 공유·파티 서버 호출. 모두 블로킹이라 [Share.io]에서 부른다. 기기 토큰은 처음 부를 때 받고,
 * 서버가 토큰을 모르면(401) 한 번 새로 받아 다시 보낸다.
 */
class ShareClient(
    private val baseUrl: String,
    private val readToken: () -> String?,
    private val writeToken: (String?) -> Unit,
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    fun courses(): Result<List<SharedCourse>> = call("GET", "/v1/courses", null, ListSerializer(SharedCourse.serializer()))
    fun upload(course: UploadCourse): Result<Uploaded> = call("POST", "/v1/courses", encode(UploadCourse.serializer(), course), Uploaded.serializer())
    fun byCode(code: String): Result<SharedCourse> = call("GET", "/v1/codes/$code", null, SharedCourse.serializer())
    fun delete(id: String): Result<Unit> = call<Unit>("DELETE", "/v1/courses/$id", null, null)
    fun finished(id: String): Result<Unit> = call<Unit>("POST", "/v1/courses/$id/runs", "", null)
    fun report(id: String): Result<Unit> = call<Unit>("POST", "/v1/courses/$id/reports", "", null)
    fun deleteMe(): Result<Unit> = call<Unit>("DELETE", "/v1/me", null, null).onSuccess { writeToken(null) }
    fun createParty(party: NewParty): Result<PartyView> = call("POST", "/v1/parties", encode(NewParty.serializer(), party), PartyView.serializer())
    fun join(code: String, profile: Profile): Result<PartyView> = call("POST", "/v1/parties/$code/join", encode(Profile.serializer(), profile), PartyView.serializer())
    fun party(code: String): Result<PartyView> = call("GET", "/v1/parties/$code", null, PartyView.serializer())
    fun parties(): Result<List<PartyView>> = call("GET", "/v1/parties", null, ListSerializer(PartyView.serializer()))
    fun sharing(code: String, on: Boolean): Result<Unit> = call<Unit>("PUT", "/v1/parties/$code/sharing", encode(Toggle.serializer(), Toggle(on)), null)
    fun position(code: String, position: Position): Result<Unit> = call<Unit>("PUT", "/v1/parties/$code/position", encode(Position.serializer(), position), null)
    fun star(code: String, on: Boolean): Result<Unit> = call<Unit>("PUT", "/v1/parties/$code/star", encode(Toggle.serializer(), Toggle(on)), null)
    fun end(code: String): Result<Unit> = call<Unit>("POST", "/v1/parties/$code/end", "", null)
    fun leave(code: String): Result<Unit> = call<Unit>("POST", "/v1/parties/$code/leave", "", null)
    fun reopen(code: String, courseId: String?): Result<PartyView> = call("POST", "/v1/parties/$code/reopen", encode(Reopen.serializer(), Reopen(courseId)), PartyView.serializer())
    fun propose(code: String, courseId: String): Result<PartyView> = call("POST", "/v1/parties/$code/course", encode(Propose.serializer(), Propose(courseId)), PartyView.serializer())
    /** 리더의 코스가 끝나면 코스를 비운다(PM 11-11). */
    fun clearCourse(code: String): Result<Unit> = call<Unit>("DELETE", "/v1/parties/$code/course", null, null)
    fun answer(code: String, proposal: Int, accept: Boolean): Result<Unit> = call<Unit>("POST", "/v1/parties/$code/answer", encode(Answer.serializer(), Answer(proposal, accept)), null)
    fun passLeader(code: String, memberId: String): Result<Unit> = call<Unit>("POST", "/v1/parties/$code/leader", encode(MemberRef.serializer(), MemberRef(memberId)), null)
    fun kick(code: String, memberId: String): Result<Unit> = call<Unit>("POST", "/v1/parties/$code/kick", encode(MemberRef.serializer(), MemberRef(memberId)), null)

    private fun <T> encode(serializer: KSerializer<T>, value: T) = json.encodeToString(serializer, value)

    @Suppress("UNCHECKED_CAST")
    private fun <T> call(method: String, path: String, body: String?, serializer: KSerializer<T>?): Result<T> {
        if (baseUrl.isEmpty()) return Result.failure(ShareError.Offline())
        return runCatching {
            val first = send(method, path, body, token())
            val response = if (first.first == 401) send(method, path, body, token(renew = true)) else first
            val (code, text) = response
            if (code !in 200..299) throw ShareError.Http(code, runCatching { json.decodeFromString(Problem.serializer(), text).message }.getOrDefault("서버 오류 $code"))
            if (serializer == null) Unit as T else json.decodeFromString(serializer, text)
        }.recoverCatching { throw if (it is IOException) ShareError.Network(it) else it }
    }

    private fun token(renew: Boolean = false): String {
        if (!renew) readToken()?.let { return it }
        val (code, text) = send("POST", "/v1/devices", "", null)
        if (code != 201) throw ShareError.Http(code, "기기 토큰을 받지 못했습니다")
        return json.decodeFromString(DeviceToken.serializer(), text).token.also(writeToken)
    }

    private fun send(method: String, path: String, body: String?, token: String?): Pair<Int, String> {
        val request = Request.Builder().url(baseUrl + path)
            .method(method, body?.toRequestBody(jsonType))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        return http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
    }
}

/** 앱 전체에서 하나. 서버 호출은 [io] 한 줄로 차례대로 보낸다. */
object Share {
    private const val PREFS = "share"
    private const val TOKEN = "token"
    val io: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var client: ShareClient? = null

    val available: Boolean get() = BuildConfig.SERVER_URL.isNotEmpty()

    fun client(context: Context): ShareClient = client ?: synchronized(this) {
        client ?: run {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ShareClient(
                BuildConfig.SERVER_URL,
                { prefs.getString(TOKEN, null) },
                { token -> prefs.edit().apply { if (token == null) remove(TOKEN) else putString(TOKEN, token) }.apply() },
                shareHttp(),
            ).also { client = it }
        }
    }
}

/** 파티에서 쓰는 닉네임과 아바타. 이 차에만 둔다. */
object MyProfile {
    private const val PREFS = "share"
    private const val NICKNAME = "nickname"
    private const val AVATAR = "avatar"

    fun get(context: Context): Profile? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nickname = prefs.getString(NICKNAME, null) ?: return null
        return Profile(nickname, prefs.getString(AVATAR, null) ?: return null)
    }

    fun set(context: Context, profile: Profile?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (profile == null) remove(NICKNAME).remove(AVATAR) else putString(NICKNAME, profile.nickname).putString(AVATAR, profile.avatar)
        }.apply()
    }
}
