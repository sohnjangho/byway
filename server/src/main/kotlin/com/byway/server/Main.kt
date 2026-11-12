package com.byway.server

import com.byway.core.course.Course
import com.byway.core.course.parseCourse
import com.byway.core.share.Problem
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/** 요청을 처리하는 데 필요한 것 한 묶음. [builtIn]은 앱에 든 코스다(파티 코스로 쓸 수 있다). */
class Server(val store: Store, val builtIn: List<Course>, val clock: () -> Long, val demoMembers: Int) {
    val positions = Positions(clock)
}

fun Application.byway(store: Store, builtIn: List<Course>, clock: () -> Long = System::currentTimeMillis, demoMembers: Int = 0) {
    val server = Server(store, builtIn, clock, demoMembers)
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(StatusPages) {
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Problem("요청 형식이 맞지 않습니다")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, Problem("요청 형식이 맞지 않습니다")) }
    }
    routing {
        courseRoutes(server)
        partyRoutes(server)
    }
}

/**
 * gpu-wsl 에서 테일넷으로만 연다(공유 설계 6-1). 주소는 환경 변수로 받는다.
 * BYWAY_HOST(기본 127.0.0.1), BYWAY_PORT(8787), BYWAY_DB(server/data/byway.db), BYWAY_COURSES(앱 코스 폴더), BYWAY_DEMO_MEMBERS(0).
 */
fun main() {
    val env = System.getenv()
    val store = Store.open(File(env["BYWAY_DB"] ?: "server/data/byway.db"))
    val builtIn = File(env["BYWAY_COURSES"] ?: "app/src/main/assets/courses").listFiles().orEmpty()
        .filter { it.extension == "json" }.map { parseCourse(it.readText()) }
    val demo = env["BYWAY_DEMO_MEMBERS"]?.toIntOrNull() ?: 0
    embeddedServer(Netty, port = env["BYWAY_PORT"]?.toIntOrNull() ?: 8787, host = env["BYWAY_HOST"] ?: "127.0.0.1") {
        byway(store, builtIn, demoMembers = demo)
    }.start(wait = true)
}
