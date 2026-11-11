package com.byway.service

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class ShareClientTest {
    private var token: String? = null

    @Test
    fun emptyUrlFailsFast() {
        val client = ShareClient("", { token }, { token = it }, OkHttpClient())
        val started = System.currentTimeMillis()
        val result = client.courses()
        assertTrue(result.exceptionOrNull() is ShareError.Offline)
        assertTrue(System.currentTimeMillis() - started < 100)
    }

    @Test
    fun timeoutIsFailureNotCrash() {
        ServerSocket(0).use { silent ->
            val accepted = mutableListOf<java.net.Socket>()
            thread(isDaemon = true) { runCatching { while (true) accepted += silent.accept() } }
            val client = ShareClient("http://127.0.0.1:${silent.localPort}", { "t" }, { token = it }, shareHttp())
            val started = System.currentTimeMillis()
            val result = client.courses()
            val took = System.currentTimeMillis() - started
            assertTrue(result.exceptionOrNull() is ShareError.Network)
            assertTrue("took $took", took in 4_000..9_000)
        }
    }

    /** 함께달리기 호출이 서버 경로와 맞는다(함께달리기 설계 6-9). */
    @Test
    fun partyCallsUseTheirPaths() {
        ServerSocket(0).use { server ->
            val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
            thread(isDaemon = true) {
                runCatching {
                    while (true) {
                        server.accept().use { socket ->
                            val input = socket.getInputStream().bufferedReader()
                            lines += input.readLine()
                            var length = 0
                            while (true) {
                                val header = input.readLine() ?: break
                                if (header.isEmpty()) break
                                if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(":").trim().toInt()
                            }
                            repeat(length) { input.read() }
                            socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        }
                    }
                }
            }
            val client = ShareClient("http://127.0.0.1:${server.localPort}", { "t" }, { token = it }, OkHttpClient())
            client.propose("ABC234", "c1")
            client.answer("ABC234", 2, accept = true)
            client.passLeader("ABC234", "m-1")
            client.kick("ABC234", "m-2")
            client.reopen("ABC234", null)
            client.clearCourse("ABC234")
            assertEquals(
                listOf("course", "answer", "leader", "kick", "reopen").map { "POST /v1/parties/ABC234/$it HTTP/1.1" } +
                    "DELETE /v1/parties/ABC234/course HTTP/1.1",
                lines.toList(),
            )
        }
    }
}
