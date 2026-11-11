package com.byway.server

import com.byway.core.course.Course
import com.byway.core.course.parseCourse
import com.byway.core.course.writeCourse
import com.byway.core.engine.DriveTuning
import com.byway.core.engine.planStops
import com.byway.core.record.RecordRules
import com.byway.core.record.nameProblem
import com.byway.core.share.DeviceToken
import com.byway.core.share.Problem
import com.byway.core.share.SharedCourse
import com.byway.core.share.UploadCourse
import com.byway.core.share.Uploaded
import com.byway.core.share.Visibility
import com.byway.core.share.newCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.random.Random

/** 기기 토큰과 코스 공유. 범위별로 보이는 사람은 공유 설계 6-4. */
fun Route.courseRoutes(server: Server) {
    val store = server.store

    post("/v1/devices") {
        val token = newToken()
        store.addDevice(newId("d-"), hashOf(token), server.clock())
        call.respond(HttpStatusCode.Created, DeviceToken(token))
    }

    get("/v1/courses") {
        val me = call.device(store) ?: return@get
        call.respond(store.visibleCourses(me).map { it.toShared(me) })
    }

    post("/v1/courses") {
        val me = call.device(store) ?: return@post
        val upload = call.receive<UploadCourse>()
        val problem = uploadProblem(server, me, upload)
        if (problem != null) return@post call.respond(HttpStatusCode.BadRequest, Problem(problem))
        val id = newId("s-")
        val code = if (upload.visibility == Visibility.PARTY) null else server.freeCode()
        val body = writeCourse(parseCourse(upload.body).copy(id = id))
        val partyCode = upload.partyCode.takeIf { upload.visibility == Visibility.PARTY }
        store.addCourse(CourseRow(id, code, upload.visibility, partyCode, me, body, 0, false), server.clock())
        call.respond(HttpStatusCode.Created, Uploaded(id, code))
    }

    get("/v1/codes/{code}") {
        val me = call.device(store) ?: return@get
        val row = store.courseByCode(call.parameters["code"].orEmpty().uppercase())
        if (row == null || row.hidden || row.visibility == Visibility.PARTY) return@get call.notFound()
        call.respond(row.toShared(me))
    }

    delete("/v1/courses/{id}") {
        val me = call.device(store) ?: return@delete
        val row = store.course(call.parameters["id"].orEmpty()) ?: return@delete call.notFound()
        if (row.owner != me) return@delete call.respond(HttpStatusCode.Forbidden, Problem("올린 차에서만 지울 수 있습니다"))
        store.deleteCourse(row.id)
        call.respond(HttpStatusCode.NoContent)
    }

    post("/v1/courses/{id}/runs") {
        call.device(store) ?: return@post
        if (store.addFinish(call.parameters["id"].orEmpty())) call.respond(HttpStatusCode.NoContent) else call.notFound()
    }

    post("/v1/courses/{id}/reports") {
        call.device(store) ?: return@post
        val id = call.parameters["id"].orEmpty()
        if (!store.hide(id)) return@post call.notFound()
        call.application.environment.log.warn("report $id")
        call.respond(HttpStatusCode.NoContent)
    }

    delete("/v1/me") {
        val me = call.device(store) ?: return@delete
        store.partiesOf(me).forEach { server.leaveParty(it, me) }
        server.positions.forget(me)
        store.deleteDevice(me)
        call.respond(HttpStatusCode.NoContent)
    }
}

/** 올릴 수 없는 이유. 앱이 이미 검사했어도 서버가 다시 한다. */
private fun uploadProblem(server: Server, me: String, upload: UploadCourse): String? {
    if (upload.visibility !in Visibility.ALL) return "모르는 공유 범위입니다"
    val course = runCatching { parseCourse(upload.body) }.getOrNull() ?: return "코스 형식이 맞지 않습니다"
    nameProblem(course.name)?.let { return it }
    if (lengthOf(course) < RecordRules().minMeters) return "5km보다 짧은 코스는 공유할 수 없습니다"
    if (runCatching { planStops(course, DriveTuning().simplifyToleranceMeters) }.getOrNull().isNullOrEmpty()) return "내비에 올릴 수 없는 코스입니다"
    if (upload.visibility == Visibility.PARTY) {
        val code = upload.partyCode ?: return "함께 달리기를 골라 주세요"
        if (server.store.party(code) == null || server.store.members(code).none { it.device == me && !it.blocked }) return "그 함께 달리기에 들어 있지 않습니다"
    }
    return null
}

private fun lengthOf(course: Course): Double = course.path.zipWithNext { a, b -> a.distanceTo(b) }.sum()

/** 파티 코스는 코드가 없다. 남의 링크 코스는 목록에 오지 않으니 코드를 아는 사람에게만 간다. */
internal fun CourseRow.toShared(me: String) = SharedCourse(
    id = id,
    code = code,
    visibility = visibility,
    finishes = finishes,
    mine = owner == me,
    body = body,
    partyCode = partyCode,
)

internal fun Server.freeCode(): String = generateSequence { newCode(Random.Default) }.first { !store.codeTaken(it) }

internal suspend fun ApplicationCall.notFound() = respond(HttpStatusCode.NotFound, Problem("찾을 수 없습니다"))
