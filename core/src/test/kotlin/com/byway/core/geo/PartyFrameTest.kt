package com.byway.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class PartyFrameTest {
    private val me = GeoPoint(37.5, 127.3)
    private val aspect = 841.0 / 580.0
    private val perDegree = Math.toRadians(1.0) * 6_371_000.0

    /** [from]에서 동쪽으로 [eastM], 북쪽으로 [northM] 옮긴 점. */
    private fun offset(from: GeoPoint, eastM: Double, northM: Double) =
        GeoPoint(from.lat + northM / perDegree, from.lon + eastM / (perDegree * cos(Math.toRadians(from.lat))))

    private fun frameOf(at: GeoPoint, members: List<GeoPoint>, current: GeoFrame? = null): GeoFrame =
        requireNotNull(partyFrame(at, members, aspect, current)).frame

    @Test
    fun farMembersAreLeftOutAndCounted() {
        val near = offset(me, 1_000.0, 0.0)
        val far = offset(me, 0.0, 20_000.0)
        val got = requireNotNull(partyFrame(me, listOf(near, far), aspect, null))
        assertEquals(1, got.far)
        assertTrue(got.frame.contains(near))
        assertFalse(got.frame.contains(far))
        assertTrue(got.frame.widthMeters < 5_000.0)
    }

    @Test
    fun noNearMemberMeansNoFrame() {
        assertNull(partyFrame(me, emptyList(), aspect, null))
        assertNull(partyFrame(me, listOf(offset(me, 7_000.0, 0.0)), aspect, null))
    }

    @Test
    fun badAspectGivesNoFrame() {
        val near = listOf(offset(me, 1_000.0, 0.0))
        listOf(0.0, -1.0, Double.NaN).forEach { assertNull(partyFrame(me, near, it, null)) }
    }

    @Test
    fun fitsAllPointsWithMargin() {
        val member = offset(me, 2_000.0, 0.0)
        val frame = frameOf(me, listOf(member))
        assertTrue(frame.contains(me, 0.8))
        assertTrue(frame.contains(member, 0.8))
        assertEquals(2_800.0, frame.widthMeters, 1.0)
    }

    /** 바로 옆이나 같은 자리의 멤버도 1.5km 폭은 지킨다. */
    @Test
    fun neverNarrowerThanMinimum() {
        listOf(offset(me, 100.0, 0.0), me).forEach { member ->
            assertEquals(1_500.0, frameOf(me, listOf(member)).widthMeters, 1e-6)
        }
    }

    @Test
    fun keepsAspect() {
        listOf(offset(me, 2_000.0, 0.0), offset(me, 0.0, 3_000.0), offset(me, 100.0, 0.0)).forEach { member ->
            val frame = frameOf(me, listOf(member))
            assertEquals(aspect, frame.widthMeters / frame.heightMeters, 1e-6)
        }
    }

    @Test
    fun keepsFrameWhilePointsStayInside() {
        val member = offset(me, 3_000.0, 0.0)
        val first = frameOf(me, listOf(member))
        assertEquals(first, frameOf(offset(me, 100.0, 0.0), listOf(member), first))
    }

    @Test
    fun reframesWhenAPointLeavesTheInnerArea() {
        val first = frameOf(me, listOf(offset(me, 3_000.0, 0.0)))
        val edge = offset(first.center, first.widthMeters / 2 * 0.95, 0.0)
        val next = frameOf(me, listOf(edge), first)
        assertNotEquals(first, next)
        assertTrue(next.contains(me, 0.8))
        assertTrue(next.contains(edge, 0.8))
    }

    @Test
    fun zoomsInWhenPointsGather() {
        val first = frameOf(me, listOf(offset(me, 4_000.0, 0.0)))
        assertEquals(5_600.0, first.widthMeters, 1.0)
        assertEquals(1_500.0, frameOf(me, listOf(offset(me, 500.0, 0.0)), first).widthMeters, 1e-6)
    }

    /** 가장 좁은 폭에서는 점이 모여 있어도 매번 중심을 옮기지 않는다. 지도가 흔들리지 않게 한다. */
    @Test
    fun staysPutAtMinimumWidth() {
        val first = frameOf(me, listOf(offset(me, 200.0, 0.0)))
        assertEquals(1_500.0, first.widthMeters, 1e-6)
        assertEquals(first, frameOf(offset(me, 50.0, 0.0), listOf(offset(me, 220.0, 0.0)), first))
    }

    /** 5km 언저리에서 GPS가 흔들려도 확대가 켜졌다 꺼졌다 하지 않는다. 확대 중이면 5.5km까지 남긴다. */
    @Test
    fun memberAtBoundaryDoesNotToggle() {
        val first = frameOf(me, listOf(offset(me, 4_900.0, 0.0)))
        val moved = offset(me, 5_200.0, 0.0)
        assertNull(partyFrame(me, listOf(moved), aspect, null))
        val kept = requireNotNull(partyFrame(me, listOf(moved), aspect, first))
        assertEquals(0, kept.far)
        assertNull(partyFrame(me, listOf(offset(me, 5_600.0, 0.0)), aspect, first))
    }
}
