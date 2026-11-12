package com.byway.server

import com.byway.core.geo.GeoPoint
import com.byway.core.share.NewParty
import com.byway.core.share.PartyView
import com.byway.core.share.Position
import com.byway.core.share.Profile
import com.byway.core.share.Reopen
import com.byway.core.share.Toggle
import com.byway.core.share.Uploaded
import com.byway.core.share.isCode
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyRoutesTest {
    private val river = longCourse.copy(id = "test-river")
    private var now = 1_000_000L

    private fun ApplicationTestBuilder.startParty(demoMembers: Int = 0) {
        application { byway(Store.open(tempDb()), builtIn = listOf(river), clock = { now }, demoMembers = demoMembers) }
    }

    private fun profile(name: String) = Profile(name, "avatar-03")

    private suspend fun HttpClient.create(token: String, courseId: String = river.id, nickname: String = "하나") =
        send("/v1/parties", token, Json.encodeToString(NewParty(courseId, "", profile(nickname))))

    private suspend fun HttpClient.newParty(token: String): PartyView = Json.decodeFromString(create(token).bodyAsText())

    private suspend fun HttpClient.join(token: String, code: String, nickname: String = "둘") =
        send("/v1/parties/$code/join", token, Json.encodeToString(profile(nickname)))

    private suspend fun HttpClient.view(token: String, code: String) = authed("/v1/parties/$code", token)

    private suspend fun HttpClient.seen(token: String, code: String): PartyView = Json.decodeFromString(view(token, code).bodyAsText())

    private suspend fun HttpClient.sharing(token: String, code: String, on: Boolean) =
        send("/v1/parties/$code/sharing", token, Json.encodeToString(Toggle(on)), "PUT")

    private suspend fun HttpClient.at(token: String, code: String, point: GeoPoint) =
        send("/v1/parties/$code/position", token, Json.encodeToString(Position(point.lat, point.lon)), "PUT")

    private suspend fun HttpClient.star(token: String, code: String) = send("/v1/parties/$code/star", token, Json.encodeToString(Toggle(true)), "PUT")

    private suspend fun HttpClient.end(token: String, code: String) = send("/v1/parties/$code/end", token, "")

    /** 코스 위 가장 가까운 점까지의 누적 거리. */
    private fun alongOf(point: GeoPoint): Double {
        val path = river.path
        val nearest = path.indices.minBy { path[it].distanceTo(point) }
        return path.zipWithNext { a, b -> a.distanceTo(b) }.take(nearest).sum()
    }

    @Test
    fun createAndJoinShowsMembers() = testApplication {
        startParty()
        val a = client.token()
        val created = client.create(a)
        assertEquals(HttpStatusCode.Created, created.status)
        val party = Json.decodeFromString<PartyView>(created.bodyAsText())
        assertTrue(isCode(party.code))
        assertTrue(party.owner && party.open)
        assertEquals("하나의 파티", party.name)
        val b = client.token()
        assertEquals(HttpStatusCode.OK, client.join(b, party.code).status)
        val fromA = client.seen(a, party.code)
        assertEquals(listOf("하나", "둘"), fromA.members.map { it.nickname })
        assertEquals(listOf(true, false), fromA.members.map { it.me })
        assertFalse(client.seen(b, party.code).owner)
    }

    @Test
    fun badProfileIsRejected() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val b = client.token()
        assertEquals(HttpStatusCode.BadRequest, client.send("/v1/parties/$code/join", b, Json.encodeToString(Profile("열한글자닉네임입니다요", "avatar-01"))).status)
        assertEquals(HttpStatusCode.BadRequest, client.send("/v1/parties/$code/join", b, Json.encodeToString(Profile("둘", "photo.jpg"))).status)
    }

    @Test
    fun positionNeedsSharingOn() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        assertEquals(HttpStatusCode.Conflict, client.at(a, code, river.path[10]).status)
        assertEquals(HttpStatusCode.NoContent, client.sharing(a, code, true).status)
        assertEquals(HttpStatusCode.NoContent, client.at(a, code, river.path[10]).status)
    }

    @Test
    fun sharingOffDeletesPositionNow() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val b = client.token()
        client.join(b, code)
        client.sharing(a, code, true)
        client.at(a, code, river.path[10])
        assertEquals(1, client.seen(b, code).pins.size)
        client.sharing(a, code, false)
        assertTrue(client.seen(b, code).pins.isEmpty())
    }

    @Test
    fun positionsExpireAfterSixtySeconds() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        client.sharing(a, code, true)
        client.at(a, code, river.path[10])
        now += 59_000
        assertEquals(1, client.seen(a, code).pins.size)
        now += 2_000
        assertTrue(client.seen(a, code).pins.isEmpty())
    }

    @Test
    fun endDeletesPositionsAndUnstarredMembers() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val b = client.token()
        client.join(b, code)
        client.star(b, code)
        client.sharing(b, code, true)
        client.at(b, code, river.path[10])
        assertEquals(HttpStatusCode.Forbidden, client.end(b, code).status)
        assertEquals(HttpStatusCode.NoContent, client.end(a, code).status)
        val after = client.seen(b, code)
        assertFalse(after.open)
        assertEquals(listOf("둘"), after.members.map { it.nickname })
        assertTrue(after.pins.isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.view(a, code).status)
    }

    @Test
    fun starredPartySurvivesEndAndReopens() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val b = client.token()
        client.join(b, code)
        client.star(b, code)
        client.end(a, code)
        val mine = Json.decodeFromString<List<PartyView>>(client.authed("/v1/parties", b).bodyAsText())
        assertEquals(listOf(code), mine.map { it.code })
        assertEquals(HttpStatusCode.NotFound, client.join(a, code).status)
        val reopened = client.send("/v1/parties/$code/reopen", b, Json.encodeToString(Reopen(river.id)))
        assertEquals(HttpStatusCode.OK, reopened.status)
        assertTrue(Json.decodeFromString<PartyView>(reopened.bodyAsText()).open)
        assertEquals(HttpStatusCode.OK, client.join(a, code).status)
    }

    @Test
    fun unstarredPartyIsGoneAfterEnd() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val b = client.token()
        client.join(b, code)
        client.end(a, code)
        assertEquals(HttpStatusCode.NotFound, client.view(b, code).status)
        assertEquals(HttpStatusCode.NotFound, client.join(client.token(), code).status)
        assertTrue(Json.decodeFromString<List<PartyView>>(client.authed("/v1/parties", b).bodyAsText()).isEmpty())
    }

    @Test
    fun nonMemberCannotSeeParty() = testApplication {
        startParty()
        val code = client.newParty(client.token()).code
        assertEquals(HttpStatusCode.NotFound, client.view(client.token(), code).status)
    }

    @Test
    fun partyCourseVisibleToMembersOnly() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val b = client.token()
        client.join(b, code)
        val uploaded = Json.decodeFromString<Uploaded>(client.upload(a, "party", partyCode = code).bodyAsText())
        assertNull(uploaded.code)
        assertEquals(listOf(uploaded.id), client.courses(b).map { it.id })
        assertTrue(client.courses(client.token()).isEmpty())
    }

    @Test
    fun othersPartyCourseCannotStartParty() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val id = Json.decodeFromString<Uploaded>(client.upload(a, "party", partyCode = code).bodyAsText()).id
        val b = client.token()
        client.join(b, code)
        assertEquals(HttpStatusCode.BadRequest, client.create(b, id).status)
        assertEquals(HttpStatusCode.Created, client.create(a, id).status)
        assertEquals(HttpStatusCode.BadRequest, client.create(a, "no-such-course").status)
    }

    @Test
    fun demoMembersFollowRealCar() = testApplication {
        startParty(demoMembers = 2)
        val a = client.token()
        val code = client.newParty(a).code
        val first = client.seen(a, code)
        assertEquals(listOf("하나", "시연용 1", "시연용 2"), first.members.map { it.nickname })
        assertEquals(listOf(false, true, true), first.members.map { it.demo })
        now += 60_000
        val moving = client.seen(a, code).pins.associate { it.memberId to alongOf(GeoPoint(it.lat, it.lon)) }
        val (lead, trail) = first.members.drop(1).map { moving.getValue(it.id) }
        assertEquals(1_000.0, lead, 150.0)
        assertEquals(0.0, trail, 150.0)
        client.sharing(a, code, true)
        val car = river.path[river.path.size / 2]
        client.at(a, code, car)
        val following = client.seen(a, code).pins.associate { it.memberId to alongOf(GeoPoint(it.lat, it.lon)) }
        assertEquals(alongOf(car) + 1_000.0, following.getValue(first.members[1].id), 150.0)
        assertEquals(alongOf(car) - 1_000.0, following.getValue(first.members[2].id), 150.0)
    }

    /** 끝난 파티를 다시 열면 위치 공유를 다시 동의해야 한다. */
    @Test
    fun endTurnsSharingOffForStarredMembers() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        client.star(a, code)
        client.sharing(a, code, true)
        client.end(a, code)
        client.send("/v1/parties/$code/reopen", a, Json.encodeToString(Reopen(river.id)))
        assertFalse(client.seen(a, code).members.single { it.me }.sharing)
        assertEquals(HttpStatusCode.Conflict, client.at(a, code, river.path[10]).status)
    }

    /** 끝난 파티에서 별표를 끄면 내 닉네임·아바타를 지운다. 아무도 남지 않으면 파티도 지운다. */
    @Test
    fun unstarringAnEndedPartyForgetsMe() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        client.star(a, code)
        client.end(a, code)
        assertEquals(HttpStatusCode.NoContent, client.send("/v1/parties/$code/star", a, Json.encodeToString(Toggle(false)), "PUT").status)
        assertEquals(HttpStatusCode.NotFound, client.view(a, code).status)
        assertTrue(Json.decodeFromString<List<PartyView>>(client.authed("/v1/parties", a).bodyAsText()).isEmpty())
    }

    @Test
    fun pinsCarryTheirAge() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        client.sharing(a, code, true)
        client.at(a, code, river.path[10])
        now += 10_000
        assertEquals(10_000L, client.seen(a, code).pins.single().ageMs)
    }

    /** 링크 코스로 연 파티도 멤버는 그 코스를 받는다(공유 설계 6-5 "기기에 없으면 함께 받는다"). */
    @Test
    fun membersGetThePartyCourse() = testApplication {
        startParty()
        val a = client.token()
        val link = Json.decodeFromString<Uploaded>(client.upload(a, "link").bodyAsText()).id
        val code = Json.decodeFromString<PartyView>(client.create(a, link).bodyAsText()).code
        val b = client.token()
        assertTrue(client.courses(b).isEmpty())
        client.join(b, code)
        assertEquals(listOf(link), client.courses(b).map { it.id })
    }

    @Test
    fun leaveLastMemberEnds() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        assertEquals(HttpStatusCode.NoContent, client.send("/v1/parties/$code/leave", a, "").status)
        assertEquals(HttpStatusCode.NotFound, client.join(client.token(), code).status)
    }

    @Test
    fun deleteMeRemovesMyPositionAndMembership() = testApplication {
        startParty()
        val a = client.token()
        val code = client.newParty(a).code
        val b = client.token()
        client.join(b, code)
        client.sharing(b, code, true)
        client.at(b, code, river.path[10])
        client.delete("/v1/me") { header(HttpHeaders.Authorization, "Bearer $b") }
        val left = client.seen(a, code)
        assertEquals(listOf("하나"), left.members.map { it.nickname })
        assertTrue(left.pins.isEmpty())
    }
}
