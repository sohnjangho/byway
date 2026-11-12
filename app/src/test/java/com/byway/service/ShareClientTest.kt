package com.byway.service

import okhttp3.OkHttpClient
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
}
