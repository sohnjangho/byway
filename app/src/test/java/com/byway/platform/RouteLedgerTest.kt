package com.byway.platform

import com.byway.core.geo.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLedgerTest {
    private val ours = GeoPoint(37.5, 127.2)
    private val theirs = GeoPoint(37.6, 127.0)
    private fun sent(atMs: Long = 0) = RouteLedger().also { it.requested(ours, atMs) }

    /** 내비는 목적지를 가까운 도로 위로 옮긴다. 약 110m 옮겨져도 우리 것이다. */
    @Test
    fun aDestinationSnappedToTheRoadIsStillOurs() {
        val ledger = sent()
        assertTrue(ledger.seen(GeoPoint(37.501, 127.2)))
        assertTrue(ledger.oursIsActive)
    }

    @Test
    fun aDestinationWeNeverSentIsTheDrivers() {
        val ledger = sent()
        assertFalse(ledger.seen(theirs))
        assertFalse(ledger.oursIsActive)
    }

    @Test
    fun nothingSentMeansNothingIsOurs() {
        val ledger = RouteLedger()
        assertFalse(ledger.oursIsActive)
        assertFalse(ledger.seen(ours))
        assertFalse(ledger.cancelIsOurs(0))
    }

    /** 경로를 연달아 올리면 앞 요청의 이벤트가 늦게 온다. 그것을 운전자가 바꾼 것으로 보지 않는다. */
    @Test
    fun aLateEventForAnEarlierRequestIsStillOurs() {
        val ledger = sent()
        ledger.requested(GeoPoint(37.7, 127.3), 1_000)
        assertTrue(ledger.seen(ours))
    }

    @Test
    fun onlyTheLastFewRequestsAreRemembered() {
        val ledger = RouteLedger(keep = 2)
        ledger.requested(ours, 0)
        ledger.requested(GeoPoint(37.7, 127.3), 1)
        ledger.requested(GeoPoint(37.8, 127.4), 2)
        assertFalse(ledger.seen(ours))
    }

    /** 실측: 경로가 떠 있을 때 새 경로를 올리면 내비가 앞 경로의 "꺼짐"을 먼저 알리고 몇 초 뒤 시작을 알린다. */
    @Test
    fun aCancelWhileOurRequestIsStartingIsOurOwnReplacement() {
        val ledger = sent(atMs = 10_000)
        assertTrue(ledger.cancelIsOurs(10_200))
        assertTrue(ledger.oursIsActive)
    }

    /** 우리 경로가 시작된 뒤에 온 "꺼짐"은 운전자가 안내를 끄거나 다른 목적지를 넣은 것이다. */
    @Test
    fun aCancelAfterOurRouteStartedIsTheDrivers() {
        val ledger = sent(atMs = 10_000)
        ledger.seen(ours)
        assertFalse(ledger.cancelIsOurs(11_000))
        assertFalse(ledger.oursIsActive)
    }

    /** 시작 이벤트가 끝내 오지 않아도 언제까지나 우리 것으로 보지는 않는다. */
    @Test
    fun waitingForTheStartDoesNotLastForever() {
        val ledger = sent(atMs = 10_000)
        assertFalse(ledger.cancelIsOurs(10_000 + 30_001))
    }

    /** 우리 목적지에 닿아 안내가 끝나며 오는 "꺼짐"은 운전자가 끈 것이 아니다. 끝난 뒤에는 지울 우리 경로가 없다. */
    @Test
    fun aCancelRightAfterArrivingAtOurDestinationIsNotTheDrivers() {
        val ledger = sent()
        ledger.seen(ours)
        ledger.arrived(50_000)
        assertTrue(ledger.cancelIsOurs(51_000))
        assertFalse(ledger.oursIsActive)
        assertFalse(ledger.cancelIsOurs(52_000))
    }

    /** 요청이 실패하면 시작을 더 기다리지 않는다. 그 뒤의 "꺼짐"은 운전자가 끈 것이다. */
    @Test
    fun aFailedRequestStopsWaitingForItsStart() {
        val ledger = sent()
        ledger.failed()
        assertFalse(ledger.cancelIsOurs(100))
        assertFalse(ledger.oursIsActive)
    }

    /** 두 번째 요청이 첫 경로를 밀어내는 취소는 첫 경로가 시작된 뒤에 온다. 마지막 요청이 확인될 때까지 우리 것이다. */
    @Test
    fun aSecondRequestStaysOursUntilItIsConfirmed() {
        val second = GeoPoint(37.7, 127.3)
        val ledger = sent(atMs = 0)
        ledger.requested(second, 1_000)
        ledger.seen(ours)
        assertTrue(ledger.cancelIsOurs(2_000))
        ledger.seen(second)
        assertFalse(ledger.cancelIsOurs(3_000))
    }

    @Test
    fun aFailedRequestIsRetriedOnce() {
        val ledger = sent()
        assertTrue(ledger.failed())
        ledger.requested(ours, 100)
        assertFalse(ledger.failed())
    }

    @Test
    fun aConfirmedRouteEarnsANewRetry() {
        val ledger = sent()
        ledger.failed()
        ledger.requested(ours, 100)
        ledger.seen(ours)
        ledger.requested(GeoPoint(37.7, 127.3), 200)
        assertTrue(ledger.failed())
    }

    /** 운전자 경로 위에 올린 요청이 실패하면 지금 경로는 여전히 운전자 것이다. 끝낼 때 지우면 안 된다. */
    @Test
    fun aFailedRequestOverTheDriversRouteLeavesItTheirs() {
        val ledger = sent()
        ledger.seen(ours)
        ledger.seen(theirs)
        ledger.requested(ours, 1_000)
        assertTrue(ledger.oursIsActive)
        ledger.failed()
        assertFalse(ledger.oursIsActive)
    }

    @Test
    fun aFailedSwapLeavesOurEarlierRouteOurs() {
        val ledger = sent()
        ledger.seen(ours)
        ledger.requested(GeoPoint(37.7, 127.3), 1_000)
        ledger.failed()
        assertTrue(ledger.oursIsActive)
    }

    @Test
    fun sendingAgainAfterTheDriversRouteMakesItOurs() {
        val ledger = sent()
        ledger.seen(ours)
        ledger.seen(theirs)
        ledger.requested(ours, 90_000)
        assertTrue(ledger.oursIsActive)
        assertTrue(ledger.cancelIsOurs(90_100))
    }
}
