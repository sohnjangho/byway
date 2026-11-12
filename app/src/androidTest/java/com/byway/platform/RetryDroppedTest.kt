package com.byway.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

/** 타일 서버가 연결을 끊어도 몇 번 더 받아 오는지 본다. MapLibre 는 실패한 타일을 다시 요청하지 않는다. */
@RunWith(AndroidJUnit4::class)
class RetryDroppedTest {
    private var calls = 0

    private fun callFailing(times: Int, failure: () -> IOException): Call {
        val client = OkHttpClient.Builder()
            .addInterceptor(RetryDropped())
            .addInterceptor { chain ->
                calls++
                if (calls <= times) throw failure()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
            .build()
        return client.newCall(Request.Builder().url("https://tiles.test/1.jpg").build())
    }

    @Test
    fun droppedConnectionIsTriedAgain() {
        val response = callFailing(2) { SocketException("connection reset") }.execute()

        assertEquals(200, response.code)
        assertEquals(3, calls)
    }

    @Test
    fun givesUpAfterAFewTries() {
        assertThrows(SocketException::class.java) {
            callFailing(99) { SocketException("connection reset") }.execute()
        }

        assertEquals(3, calls)
    }

    @Test
    fun noNetworkFailsAtOnce() {
        assertThrows(UnknownHostException::class.java) {
            callFailing(99) { UnknownHostException("no address") }.execute()
        }

        assertEquals(1, calls)
    }

    @Test
    fun cancelledCallIsNotRetried() {
        lateinit var call: Call
        call = callFailing(99) {
            call.cancel()
            IOException("Canceled")
        }

        assertThrows(IOException::class.java) { call.execute() }

        assertEquals(1, calls)
    }
}
