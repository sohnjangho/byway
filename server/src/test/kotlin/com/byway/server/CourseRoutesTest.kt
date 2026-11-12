package com.byway.server

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.course.writeCourse
import com.byway.core.geo.GeoPoint
import com.byway.core.record.Recorded
import com.byway.core.record.makeCourse
import com.byway.core.share.DeviceToken
import com.byway.core.share.NewParty
import com.byway.core.share.PartyView
import com.byway.core.share.Profile
import com.byway.core.share.SharedCourse
import com.byway.core.share.UploadCourse
import com.byway.core.share.Uploaded
import com.byway.core.share.isCode
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 위도 37.5 에서 동쪽으로 100m 간격의 직선 길. */
internal fun straight(meters: Double): List<GeoPoint> =
    (0..(meters / 100.0).toInt()).map { GeoPoint(37.5, 127.0 + it * 100.0 / 88_300.0) }

internal val longCourse: Course by lazy { (makeCourse("m-local", "강변 시험 길", "river", straight(21_000.0)) as Recorded.Made).course }

internal fun tempDb(): File = File.createTempFile("byway-test", ".db").apply { delete(); deleteOnExit() }

internal fun ApplicationTestBuilder.start(db: File = tempDb(), demoMembers: Int = 0, clock: () -> Long = System::currentTimeMillis) {
    application { byway(Store.open(db), builtIn = emptyList(), clock = clock, demoMembers = demoMembers) }
}

internal suspend fun HttpClient.token(): String = Json.decodeFromString<DeviceToken>(post("/v1/devices").bodyAsText()).token

internal suspend fun HttpClient.send(path: String, token: String, body: String, method: String = "POST"): HttpResponse {
    val build: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return when (method) {
        "PUT" -> put(path, build)
        "DELETE" -> delete(path, build)
        else -> post(path, build)
    }
}

internal suspend fun HttpClient.upload(token: String, visibility: String, course: Course = longCourse, partyCode: String? = null): HttpResponse =
    send("/v1/courses", token, Json.encodeToString(UploadCourse(visibility, partyCode, writeCourse(course))))

internal suspend fun HttpClient.authed(path: String, token: String): HttpResponse = get(path) { header(HttpHeaders.Authorization, "Bearer $token") }

internal suspend fun HttpClient.courses(token: String): List<SharedCourse> = Json.decodeFromString(authed("/v1/courses", token).bodyAsText())

class CourseRoutesTest {
    @Test
    fun deviceTokenIsStoredHashed() = testApplication {
        val db = tempDb()
        start(db)
        val response = client.post("/v1/devices")
        assertEquals(HttpStatusCode.Created, response.status)
        val token = Json.decodeFromString<DeviceToken>(response.bodyAsText()).token
        assertTrue(token.length >= 32)
        assertFalse(String(db.readBytes(), Charsets.ISO_8859_1).contains(token))
    }

    @Test
    fun noTokenIs401() = testApplication {
        start()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/courses").status)
        assertEquals(HttpStatusCode.Unauthorized, client.authed("/v1/courses", "not-a-token").status)
    }

    @Test
    fun uploadValidCoursePublicGetsCode() = testApplication {
        start()
        val me = client.token()
        val response = client.upload(me, "public")
        assertEquals(HttpStatusCode.Created, response.status)
        val uploaded = Json.decodeFromString<Uploaded>(response.bodyAsText())
        assertTrue(uploaded.id.matches(Regex("s-[a-z0-9]{10}")))
        assertTrue(isCode(requireNotNull(uploaded.code)))
        val other = client.token()
        val seen = client.courses(other).single()
        assertEquals(uploaded.id, seen.id)
        assertFalse(seen.mine)
        assertTrue(seen.body.contains(uploaded.id))
    }

    @Test
    fun shortCourseIsRejected() = testApplication {
        start()
        val short = Course("m-s", "짧은 길", straight(3_000.0), listOf(Section("1구간", 0, 30, "city")), attribution = "에움길 운전자 기록")
        val response = client.upload(client.token(), "public", short)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("message"))
    }

    @Test
    fun badJsonIsRejected() = testApplication {
        start()
        val me = client.token()
        assertEquals(HttpStatusCode.BadRequest, client.send("/v1/courses", me, "{not json").status)
        val badBody = Json.encodeToString(UploadCourse("public", null, "{\"schemaVersion\":2}"))
        assertEquals(HttpStatusCode.BadRequest, client.send("/v1/courses", me, badBody).status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(me, "everyone").status)
    }

    @Test
    fun badNameIsRejected() = testApplication {
        start()
        val response = client.upload(client.token(), "public", longCourse.copy(name = "카톡 문의 길"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun publicListHidesReported() = testApplication {
        start()
        val me = client.token()
        val id = Json.decodeFromString<Uploaded>(client.upload(me, "public").bodyAsText()).id
        val other = client.token()
        assertEquals(HttpStatusCode.NoContent, client.send("/v1/courses/$id/reports", other, "").status)
        assertTrue(client.courses(other).isEmpty())
    }

    @Test
    fun linkCourseOnlyByCode() = testApplication {
        start()
        val me = client.token()
        val uploaded = Json.decodeFromString<Uploaded>(client.upload(me, "link").bodyAsText())
        val other = client.token()
        assertTrue(client.courses(other).isEmpty())
        val got = client.authed("/v1/codes/${uploaded.code}", other)
        assertEquals(HttpStatusCode.OK, got.status)
        assertEquals(uploaded.id, Json.decodeFromString<SharedCourse>(got.bodyAsText()).id)
        assertEquals(HttpStatusCode.NotFound, client.authed("/v1/codes/ZZZZZZ", other).status)
        assertEquals(uploaded.code, client.courses(me).single().code)
    }

    @Test
    fun partyUploadNeedsMembership() = testApplication {
        start()
        assertEquals(HttpStatusCode.BadRequest, client.upload(client.token(), "party", partyCode = "ABCDEF").status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(client.token(), "party").status)
    }

    @Test
    fun onlyOwnerDeletes() = testApplication {
        start()
        val me = client.token()
        val id = Json.decodeFromString<Uploaded>(client.upload(me, "public").bodyAsText()).id
        val other = client.token()
        assertEquals(HttpStatusCode.Forbidden, client.delete("/v1/courses/$id") { header(HttpHeaders.Authorization, "Bearer $other") }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/courses/$id") { header(HttpHeaders.Authorization, "Bearer $me") }.status)
        assertTrue(client.courses(other).isEmpty())
    }

    @Test
    fun finishesCount() = testApplication {
        start()
        val me = client.token()
        val id = Json.decodeFromString<Uploaded>(client.upload(me, "public").bodyAsText()).id
        repeat(3) { assertEquals(HttpStatusCode.NoContent, client.send("/v1/courses/$id/runs", client.token(), "").status) }
        assertEquals(3, client.courses(me).single().finishes)
        assertEquals(HttpStatusCode.NotFound, client.send("/v1/courses/s-nothing123/runs", me, "").status)
    }

    @Test
    fun deleteMeRemovesMyCourses() = testApplication {
        start()
        val me = client.token()
        client.upload(me, "public")
        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me") { header(HttpHeaders.Authorization, "Bearer $me") }.status)
        val other = client.token()
        assertTrue(client.courses(other).isEmpty())
        assertEquals(HttpStatusCode.Unauthorized, client.authed("/v1/courses", me).status)
    }

    /** 파티 범위 코스는 어느 파티에 묶였는지 함께 온다. 앱이 이 파티의 코스만 고를 수 있게 한다(함께달리기 설계 6-10.3). */
    @Test
    fun partyCourseCarriesItsParty() = testApplication {
        start()
        val me = client.token()
        val party = Json.decodeFromString<PartyView>(client.send("/v1/parties", me, Json.encodeToString(NewParty(null, "", Profile("하나", "avatar-01")))).bodyAsText())
        client.upload(me, "party", partyCode = party.code)
        client.upload(me, "public")
        assertEquals(listOf(party.code, null), client.courses(me).map { it.partyCode })
    }
}
