package com.byway.platform

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.UnknownHostException

/** MapLibre 는 실패한 타일을 다시 요청하지 않아 구멍으로 남는다. 서버가 끊은 연결은 여기서 몇 번 더 시도한다. */
internal class RetryDropped : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var tries = 1
        while (true) {
            try {
                return chain.proceed(chain.request())
            } catch (failure: IOException) {
                if (tries == MAX_TRIES || failure is UnknownHostException || chain.call().isCanceled()) throw failure
                Thread.sleep(WAIT_MS * tries)
                tries++
            }
        }
    }

    private companion object {
        const val MAX_TRIES = 3
        const val WAIT_MS = 300L
    }
}
