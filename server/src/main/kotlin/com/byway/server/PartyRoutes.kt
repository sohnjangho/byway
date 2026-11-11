package com.byway.server

import com.byway.core.course.Course
import com.byway.core.course.parseCourse
import com.byway.core.geo.GeoPoint
import com.byway.core.record.nameProblem
import com.byway.core.share.AVATARS
import com.byway.core.share.Answer
import com.byway.core.share.MemberRef
import com.byway.core.share.NICKNAME_MAX
import com.byway.core.share.NewParty
import com.byway.core.share.PartyMember
import com.byway.core.share.PartyPin
import com.byway.core.share.PartyView
import com.byway.core.share.Position
import com.byway.core.share.Problem
import com.byway.core.share.Profile
import com.byway.core.share.Propose
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

/**
 * 함께달리기 파티(공유 설계 6-5, 함께달리기 설계 6-2~6-10). 위치는 [Positions] 메모리에만 있다.
 * 파티는 코스 없이 열고, 리더가 나중에 코스를 고르면(제안) 파티원이 승낙하거나 거부(나감)한다.
 */
fun Route.partyRoutes(server: Server) {
    val store = server.store

    post("/v1/parties") {
        val me = call.device(store) ?: return@post
        val request = call.receive<NewParty>()
        val problem = profileProblem(request.profile) ?: request.courseId?.let { courseProblem(server, me, null, it) }
        if (problem != null) return@post call.respond(HttpStatusCode.BadRequest, Problem(problem))
        val name = request.name.trim().ifEmpty { "${request.profile.nickname.trim()}의 파티" }
        nameProblem(name)?.let { return@post call.respond(HttpStatusCode.BadRequest, Problem(it)) }
        val code = server.freeCode()
        store.addParty(PartyRow(code, name, null, me, open = true), server.clock())
        val mine = MemberRow(code, me, request.profile.nickname.trim(), request.profile.avatar, sharing = false, starred = false)
        store.putMember(mine)
        // 코스를 들고 만들면 열자마자 그 코스를 고른 것과 같다.
        request.courseId?.let { store.putMember(mine.copy(accepted = store.propose(code, it, server.clock()))) }
        call.respond(HttpStatusCode.Created, server.view(code, me))
    }

    get("/v1/parties") {
        val me = call.device(store) ?: return@get
        val mine = store.partiesOf(me).filter { code ->
            val party = store.party(code)
            val row = store.members(code).firstOrNull { it.device == me }
            party != null && row != null && !row.blocked && (party.open || row.starred)
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
        if (before?.blocked == true) return@post call.respond(HttpStatusCode.Forbidden, Problem("이 파티에 들어갈 수 없습니다"))
        store.putMember(
            MemberRow(
                party.code, me, profile.nickname.trim(), profile.avatar,
                sharing = before?.sharing ?: false, starred = before?.starred ?: false, accepted = before?.accepted ?: 0, present = true,
            ),
        )
        call.respond(server.view(party.code, me))
    }

    get("/v1/parties/{code}") {
        val me = call.device(store) ?: return@get
        val member = member(server, me)?.takeIf { !it.blocked } ?: return@get call.notFound()
        call.respond(server.view(member.partyCode, me))
    }

    put("/v1/parties/{code}/sharing") {
        val me = call.device(store) ?: return@put
        val on = call.receive<Toggle>().on
        val member = present(server, me) ?: return@put call.notFound()
        store.putMember(member.copy(sharing = on))
        if (!on) server.positions.remove(member.partyCode, me)
        call.respond(HttpStatusCode.NoContent)
    }

    put("/v1/parties/{code}/position") {
        val me = call.device(store) ?: return@put
        val position = call.receive<Position>()
        val member = present(server, me) ?: return@put call.notFound()
        if (!member.sharing || store.party(member.partyCode)?.open != true) {
            return@put call.respond(HttpStatusCode.Conflict, Problem("위치 공유가 꺼져 있습니다"))
        }
        server.positions.put(member.partyCode, me, GeoPoint(position.lat, position.lon))
        call.respond(HttpStatusCode.NoContent)
    }

    put("/v1/parties/{code}/star") {
        val me = call.device(store) ?: return@put
        val on = call.receive<Toggle>().on
        val member = member(server, me)?.takeIf { !it.blocked } ?: return@put call.notFound()
        if (!on && store.party(member.partyCode)?.open == false) {
            // 끝난 파티에서 별표를 끄면 그 파티에 남겨 둔 내 닉네임·아바타를 지운다(STAR_NOTE 약속).
            store.deleteMember(member.partyCode, me)
            if (store.members(member.partyCode).none { !it.blocked }) store.deleteParty(member.partyCode)
        } else {
            store.putMember(member.copy(starred = on))
        }
        call.respond(HttpStatusCode.NoContent)
    }

    post("/v1/parties/{code}/end") {
        val me = call.device(store) ?: return@post
        val member = present(server, me) ?: return@post call.notFound()
        if (store.party(member.partyCode)?.owner != me) return@post call.respond(HttpStatusCode.Forbidden, Problem("리더만 파티를 끝낼 수 있습니다"))
        server.endParty(member.partyCode)
        call.respond(HttpStatusCode.NoContent)
    }

    post("/v1/parties/{code}/leave") {
        val me = call.device(store) ?: return@post
        val member = present(server, me) ?: return@post call.notFound()
        server.leaveParty(member.partyCode, me)
        call.respond(HttpStatusCode.NoContent)
    }

    /** 리더가 코스를 고른다. 리더는 고르는 순간 승낙한 것이다. */
    post("/v1/parties/{code}/course") {
        val me = call.device(store) ?: return@post
        val courseId = call.receive<Propose>().courseId
        val member = present(server, me) ?: return@post call.notFound()
        val party = store.party(member.partyCode)?.takeIf { it.open } ?: return@post call.notFound()
        if (party.owner != me) return@post call.respond(HttpStatusCode.Forbidden, Problem("리더만 코스를 고를 수 있습니다"))
        courseProblem(server, me, party.code, courseId)?.let { return@post call.respond(HttpStatusCode.BadRequest, Problem(it)) }
        store.putMember(member.copy(accepted = store.propose(party.code, courseId, server.clock())))
        call.respond(server.view(party.code, me))
    }

    /** 리더가 고른 코스에 답한다. 승낙하면 그 제안 번호를 남기고, 거부하면 파티를 나간다. */
    post("/v1/parties/{code}/answer") {
        val me = call.device(store) ?: return@post
        val answer = call.receive<Answer>()
        val member = present(server, me) ?: return@post call.notFound()
        val party = store.party(member.partyCode)?.takeIf { it.open } ?: return@post call.notFound()
        if (party.courseId == null || answer.proposal != party.proposal) {
            return@post call.respond(HttpStatusCode.Conflict, Problem("리더가 코스를 다시 골랐습니다"))
        }
        if (answer.accept) store.putMember(member.copy(accepted = answer.proposal)) else server.leaveParty(party.code, me)
        call.respond(HttpStatusCode.NoContent)
    }

    post("/v1/parties/{code}/leader") {
        val me = call.device(store) ?: return@post
        val ref = call.receive<MemberRef>()
        val (party, target) = leaderTarget(server, me, ref) ?: return@post
        store.setOwner(party.code, target.device)
        call.respond(HttpStatusCode.NoContent)
    }

    /** 내보낸 기기는 닉네임·아바타를 지우고 막아 둔다. 같은 코드로 다시 들어오지 못한다. */
    post("/v1/parties/{code}/kick") {
        val me = call.device(store) ?: return@post
        val ref = call.receive<MemberRef>()
        val (party, target) = leaderTarget(server, me, ref) ?: return@post
        server.positions.remove(party.code, target.device)
        store.putMember(target.copy(nickname = "", avatar = "", sharing = false, starred = false, present = false, blocked = true))
        call.respond(HttpStatusCode.NoContent)
    }

    /** 다시 모이기. 다시 연 사람만 들어간 채로 열고 그 사람이 리더다. 다른 별표 멤버는 들어가기를 눌러야 들어온다. */
    post("/v1/parties/{code}/reopen") {
        val me = call.device(store) ?: return@post
        val courseId = call.receive<Reopen>().courseId
        val member = member(server, me)?.takeIf { !it.blocked } ?: return@post call.notFound()
        if (!member.starred || store.party(member.partyCode)?.open != false) {
            return@post call.respond(HttpStatusCode.Conflict, Problem("즐겨찾는 끝난 파티만 다시 열 수 있습니다"))
        }
        val code = member.partyCode
        courseId?.let { courseProblem(server, me, code, it) }?.let { return@post call.respond(HttpStatusCode.BadRequest, Problem(it)) }
        store.reopenParty(code, null, server.clock())
        store.setOwner(code, me)
        store.members(code).filter { it.device != me }.forEach { store.putMember(it.copy(present = false)) }
        val mine = member.copy(present = true)
        store.putMember(mine)
        courseId?.let { store.putMember(mine.copy(accepted = store.propose(code, it, server.clock()))) }
        call.respond(server.view(code, me))
    }
}

/**
 * 파티가 끝나면 위치를 모두 지우고, 스스로 별표한 멤버만 들어와 있지 않은 상태로 남긴다. 아무도 없으면 파티도 지운다.
 * 남는 멤버도 위치 공유를 끈다. 다시 모이면 다시 동의해야 위치가 나간다.
 */
internal fun Server.endParty(code: String) {
    positions.clearParty(code)
    store.members(code).forEach {
        if (it.starred && !it.blocked) store.putMember(it.copy(sharing = false, present = false)) else store.deleteMember(code, it.device)
    }
    if (store.members(code).isEmpty()) store.deleteParty(code) else store.closeParty(code)
}

/** 나가면 내 위치와 멤버 정보를 지운다. 리더가 나가면 들어온 순서로 다음 사람이 넘겨받고, 아무도 없으면 끝난다. */
internal fun Server.leaveParty(code: String, device: String) {
    positions.remove(code, device)
    store.deleteMember(code, device)
    val rest = store.members(code).filter { it.present && !it.blocked }
    when {
        rest.isEmpty() -> endParty(code)
        store.party(code)?.owner == device -> store.setOwner(code, rest.first().device)
    }
}

internal fun Server.view(code: String, me: String): PartyView {
    val party = requireNotNull(store.party(code))
    val rows = store.members(code)
    // 열린 파티는 들어와 있는 사람만, 끝난 파티는 별표로 남은 사람을 보인다(즐겨찾는 파티).
    val present = if (party.open) rows.filter { it.present && !it.blocked } else rows.filter { !it.blocked }
    val proposal = if (party.courseId != null) party.proposal else 0
    val live = positions.live(code).filter { (device, _, _) -> present.any { it.device == device && it.sharing } }
    val real = present.map {
        PartyMember(
            memberId(it.device), it.nickname, it.avatar, demo = false, me = it.device == me,
            sharing = it.sharing && it.device == me, starred = it.starred && it.device == me,
            leader = it.device == party.owner, accepted = proposal > 0 && it.accepted == proposal,
        )
    }
    val pins = live.map { (device, point, age) -> PartyPin(memberId(device), point.lat, point.lon, age) }
    val base = PartyView(
        party.code, party.name, party.courseId, party.open, party.owner == me, real, pins,
        proposal = proposal,
        leaderName = present.firstOrNull { it.device == party.owner }?.nickname.orEmpty(),
        joined = rows.firstOrNull { it.device == me }?.present == true,
    )
    if (!party.open || demoMembers == 0) return base
    // 가상 멤버는 코스를 고르기 전에도 목록에 있고, 고르면 바로 승낙한 것으로 본다. 핀은 코스가 있을 때만 찍는다.
    fun demo(i: Int) = PartyMember(
        DemoMembers.idOf(i), DemoMembers.nameOf(i), DemoMembers.avatarOf(i),
        demo = true, me = false, sharing = true, starred = false, accepted = proposal > 0,
    )
    val course = party.courseId?.let { courseOf(it) } ?: return base.copy(members = real + (0 until demoMembers).map(::demo))
    val places = DemoMembers(course.path).places(demoMembers, clock() - party.openedAt, live.firstOrNull()?.second)
    val demoPins = places.mapIndexed { i, p -> PartyPin(DemoMembers.idOf(i), p.lat, p.lon) }
    return base.copy(members = real + places.indices.map(::demo), pins = pins + demoPins)
}

/** 다른 멤버에게 기기 id 를 그대로 보이지 않는다. */
private fun memberId(device: String): String = "m-" + hashOf(device).take(12)

internal fun Server.courseOf(id: String): Course? =
    builtIn.firstOrNull { it.id == id } ?: store.course(id)?.takeIf { !it.hidden }?.let { runCatching { parseCourse(it.body) }.getOrNull() }

/**
 * 파티 코스로 쓸 수 없는 이유. 파티 범위 코스는 내가 올린 것이거나 이 파티([partyCode])에 묶인 것만 된다.
 * 다른 파티에서 받은 남의 코스는 원래 범위를 넘기므로 막는다(함께달리기 설계 6-10.3).
 */
private fun courseProblem(server: Server, me: String, partyCode: String?, id: String): String? {
    if (server.builtIn.any { it.id == id }) return null
    val row = server.store.course(id) ?: return "코스를 찾을 수 없습니다"
    if (row.hidden) return "코스를 찾을 수 없습니다"
    if (row.visibility == Visibility.PARTY && row.owner != me && (partyCode == null || row.partyCode != partyCode)) {
        return "다른 파티에서 받은 코스는 쓸 수 없습니다"
    }
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

/** 이 파티에 남아 있는 내 줄. 들어와 있지 않은 별표 줄과 막힌 줄도 돌려준다. */
private fun PipelineContext<Unit, ApplicationCall>.member(server: Server, me: String): MemberRow? {
    val code = code()
    if (server.store.party(code) == null) return null
    return server.store.members(code).firstOrNull { it.device == me }
}

/** 지금 들어와 있는 내 줄. */
private fun PipelineContext<Unit, ApplicationCall>.present(server: Server, me: String): MemberRow? =
    member(server, me)?.takeIf { it.present && !it.blocked }

/** 리더 넘기기·내보내기의 공통 검사. 리더만 하고, 대상은 들어와 있는 다른 실제 멤버다. 안 되면 응답을 보내고 null. */
private suspend fun PipelineContext<Unit, ApplicationCall>.leaderTarget(server: Server, me: String, ref: MemberRef): Pair<PartyRow, MemberRow>? {
    val member = present(server, me) ?: return null.also { call.notFound() }
    val party = server.store.party(member.partyCode)?.takeIf { it.open } ?: return null.also { call.notFound() }
    if (party.owner != me) return null.also { call.respond(HttpStatusCode.Forbidden, Problem("리더만 할 수 있습니다")) }
    val target = server.store.members(party.code).firstOrNull { it.device != me && it.present && !it.blocked && memberId(it.device) == ref.memberId }
        ?: return null.also { call.respond(HttpStatusCode.BadRequest, Problem("이 멤버에게는 할 수 없습니다")) }
    return party to target
}
