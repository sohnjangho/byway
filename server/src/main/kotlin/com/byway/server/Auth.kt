package com.byway.server

import com.byway.core.share.Problem
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private val random = SecureRandom()
private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

/** 로그인 없는 익명 기기 토큰. 서버는 해시만 둔다. */
fun newToken(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

fun hashOf(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

fun newId(prefix: String, length: Int = 10): String = prefix + String(CharArray(length) { ID_CHARS[random.nextInt(ID_CHARS.length)] })

/** 요청한 기기. 토큰이 없거나 모르는 토큰이면 401 로 답하고 null 이다. */
suspend fun ApplicationCall.device(store: Store): String? {
    val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
    val device = token?.let { store.deviceFor(hashOf(it)) }
    if (device == null) respond(HttpStatusCode.Unauthorized, Problem("기기 토큰이 필요합니다"))
    return device
}
