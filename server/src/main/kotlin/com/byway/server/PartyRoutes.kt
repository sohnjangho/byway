package com.byway.server

import com.byway.core.course.Course
import com.byway.core.course.parseCourse
import com.byway.core.geo.GeoPoint
import com.byway.core.record.nameProblem
import com.byway.core.share.AVATARS
import com.byway.core.share.NICKNAME_MAX
import com.byway.core.share.NewParty
import com.byway.core.share.PartyMember
import com.byway.core.share.PartyPin
import com.byway.core.share.PartyView
import com.byway.core.share.Position
import com.byway.core.share.Problem
import com.byway.core.share.Profile
import com.byway.core.share.Reopen
import com.byway.core.share.Toggle
import com.byway.core.share.Visibility
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.pipeline.PipelineContext

/** 한 번 달리기용 파티와 즐겨찾기(공유 설계 6-5). 위치는 [Positions] 메모리에만 있다. */
fun Route.partyRoutes(server: Server) {
    val store = server.store

    post("/v1/parties") {
        val me = call.device(store) ?: return@post
        val request = call.receive<NewParty>()
        val problem = profileProblem(request.profile) ?: courseProblem(server, me, request.courseId.orEmpty())
        if (problem != null) return@post call.respond(HttpStatusCode.BadRequest, Problem(problem))
        val name = request.name.trim().ifEmpty { "${request.profile.nickname.trim()}의 파티" }
        nameProblem(name)?.let { return@post call.respond(HttpStatusCode.BadRequest, Problem(it)) }
        val code = server.freeCode()
        store.addParty(PartyRow(code, name, request.courseId.orEmpty(), me, open = true), server.clock())
        store.putMember(MemberRow(code, me, request.profile.nickname.trim(), request.profile.avatar, sharing = false, starred = false))
        call.respond(HttpStatusCode.Created, server.view(code, me))
    }

    get("/v1/parties") {
        val me = call.device(store) ?: return@get
        val mine = store.partiesOf(me).filter { code ->
            val party = store.party(code)
            party != null && (party.open || store.members(code).any { it.device == me && it.starred })
        }
        call.respond(mine.map { server.view(it, me) })
    }

    post("/v1/parties/{code}/join") {
        val me = call.device(store) ?: return@post
        val profile = call.receive<Profile>()
        val party = store.party(code()) ?: return@post call.notFound()
        if (!party.open) return@post call.notFound()
        profileProblem(profile)?.let { return@post call.respond(HttpStatusCode.BadRequest, Problem(it)) }
        val before = store.members(party.code).firstOrNull { it.device == me }
        store.putMember(MemberRow(party.code, me, profile.nickname.trim(), profile.avatar, before?.sharing ?: false, before?.starred ?: false))
        call.respond(server.view(party.code, me))
    }

    get("/v1/parties/{code}") {
        val me = call.device(store) ?: return@get
        val member = member(server, me) ?: return@get call.notFound()
        call.respond(server.view(member.partyCode, me))
    }

    put("/v1/parties/{code}/sharing") {
        val me = call.device(store) ?: return@put
        val on = call.receive<Toggle>().on
        val member = member(server, me) ?: return@put call.notFound()
        store.putMember(member.copy(sharing = on))
        if (!on) server.positions.remove(member.partyCode, me)
        call.respond(HttpStatusCode.NoContent)
    }

    put("/v1/parties/{code}/position") {
        val me = call.device(store) ?: return@put
        val position = call.receive<Position>()
        val member = member(server, me) ?: return@put call.notFound()
        if (!member.sharing || store.party(member.partyCode)?.open != true) {
            return@put call.respond(HttpStatusCode.Conflict, Problem("위치 공유가 꺼져 있습니다"))
        }
        server.positions.put(member.partyCode, me, GeoPoint(position.lat, position.lon))
        call.respond(HttpStatusCode.NoContent)
    }

    put("/v1/parties/{code}/star") {
        val me = call.device(store) ?: return@put
        val on = call.receive<Toggle>().on
        val member = member(server, me) ?: return@put call.notFound()
        if (!on && store.party(member.partyCode)?.open == false) {
            // 끝난 파티에서 별표를 끄면 그 파티에 남겨 둔 내 닉네임·아바타를 지운다(STAR_NOTE 약속).
            store.deleteMember(member.partyCode, me)
            if (store.members(member.partyCode).isEmpty()) store.deleteParty(member.partyCode)
        } else {
            store.putMember(member.copy(starred = on))
        }
        call.respond(HttpStatusCode.NoContent)
    }

    post("/v1/parties/{code}/end") {
        val me = call.device(store) ?: return@post
        val member = member(server, me) ?: return@post call.notFound()
        if (store.party(member.partyCode)?.owner != me) return@post call.respond(HttpStatusCode.Forbidden, Problem("파티를 만든 사람만 끝낼 수 있습니다"))
        server.endParty(member.partyCode)
        call.respond(HttpStatusCode.NoContent)
    }

    post("/v1/parties/{code}/leave") {
        val me = call.device(store) ?: return@post
        val member = member(server, me) ?: return@post call.notFound()
        server.leaveParty(member.partyCode, me)
        call.respond(HttpStatusCode.NoContent)
    }

    post("/v1/parties/{code}/reopen") {
        val me = call.device(store) ?: return@post
        val courseId = call.receive<Reopen>().courseId
        val member = member(server, me) ?: return@post call.notFound()
        if (!member.starred || store.party(member.partyCode)?.open != false) {
            return@post call.respond(HttpStatusCode.Conflict, Problem("즐겨찾는 끝난 파티만 다시 열 수 있습니다"))
        }
        courseProblem(server, me, courseId.orEmpty())?.let { return@post call.respond(HttpStatusCode.BadRequest, Problem(it)) }
        store.reopenParty(member.partyCode, courseId.orEmpty(), server.clock())
        store.setOwner(member.partyCode, me)
        call.respond(server.view(member.partyCode, me))
    }
}

/**
 * 파티가 끝나면 위치를 모두 지우고, 스스로 별표한 멤버만 남긴다. 아무도 없으면 파티도 지운다.
 * 남는 멤버도 위치 공유를 끈다. 다시 열면 다시 동의해야 위치가 나간다.
 */
internal fun Server.endParty(code: String) {
    positions.clearParty(code)
    store.members(code).forEach { if (it.starred) store.putMember(it.copy(sharing = false)) else store.deleteMember(code, it.device) }
    if (store.members(code).isEmpty()) store.deleteParty(code) else store.closeParty(code)
}

/** 나가면 내 위치와 멤버 정보를 지운다. 만든 사람이 나가면 다음 멤버가 넘겨받고, 아무도 없으면 끝난다. */
internal fun Server.leaveParty(code: String, device: String) {
    positions.remove(code, device)
    store.deleteMember(code, device)
    val rest = store.members(code)
    when {
        rest.isEmpty() -> store.deleteParty(code)
        store.party(code)?.owner == device -> store.setOwner(code, rest.first().device)
    }
}

internal fun Server.view(code: String, me: String): PartyView {
    val party = requireNotNull(store.party(code))
    val members = store.members(code)
    val live = positions.live(code).filter { (device, _, _) -> members.any { it.device == device && it.sharing } }
    val real = members.map { PartyMember(memberId(it.device), it.nickname, it.avatar, demo = false, me = it.device == me, sharing = it.sharing && it.device == me, starred = it.starred && it.device == me) }
    val pins = live.map { (device, point, age) -> PartyPin(memberId(device), point.lat, point.lon, age) }
    val course = courseOf(party.courseId)
    if (!party.open || demoMembers == 0 || course == null) return PartyView(party.code, party.name, party.courseId, party.open, party.owner == me, real, pins)
    val places = DemoMembers(course.path).places(demoMembers, clock() - party.openedAt, live.firstOrNull()?.second)
    val demo = places.indices.map { PartyMember(DemoMembers.idOf(it), DemoMembers.nameOf(it), DemoMembers.avatarOf(it), demo = true, me = false, sharing = true, starred = false) }
    val demoPins = places.mapIndexed { i, p -> PartyPin(DemoMembers.idOf(i), p.lat, p.lon) }
    return PartyView(party.code, party.name, party.courseId, party.open, party.owner == me, real + demo, pins + demoPins)
}

/** 다른 멤버에게 기기 id 를 그대로 보이지 않는다. */
private fun memberId(device: String): String = "m-" + hashOf(device).take(12)

internal fun Server.courseOf(id: String): Course? =
    builtIn.firstOrNull { it.id == id } ?: store.course(id)?.takeIf { !it.hidden }?.let { runCatching { parseCourse(it.body) }.getOrNull() }

/** 파티 코스로 쓸 수 없는 이유. 남의 파티 코스는 원래 범위를 넘기므로 막는다. */
private fun courseProblem(server: Server, me: String, id: String): String? {
    if (server.builtIn.any { it.id == id }) return null
    val row = server.store.course(id) ?: return "코스를 찾을 수 없습니다"
    if (row.hidden) return "코스를 찾을 수 없습니다"
    if (row.visibility == Visibility.PARTY && row.owner != me) return "다른 파티에서 받은 코스는 쓸 수 없습니다"
    return null
}

private fun profileProblem(profile: Profile): String? {
    val nickname = profile.nickname.trim()
    return when {
        nickname.isEmpty() -> "닉네임을 넣어 주세요"
        nickname.length > NICKNAME_MAX -> "닉네임은 ${NICKNAME_MAX}자 안으로 넣어 주세요"
        profile.avatar !in AVATARS -> "아바타를 골라 주세요"
        else -> nameProblem(nickname)
    }
}

private fun PipelineContext<Unit, ApplicationCall>.code(): String = call.parameters["code"].orEmpty().uppercase()

private fun PipelineContext<Unit, ApplicationCall>.member(server: Server, me: String): MemberRow? {
    val code = code()
    if (server.store.party(code) == null) return null
    return server.store.members(code).firstOrNull { it.device == me }
}
