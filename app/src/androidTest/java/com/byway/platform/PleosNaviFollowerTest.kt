package com.byway.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byway.core.drive.RouteAbility
import com.byway.core.drive.RouteFollower
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 내장 내비는 경유지 2개씩만 받는다. 자율주행 구현은 같은 자리에 다른 능력으로 들어온다. */
@RunWith(AndroidJUnit4::class)
class PleosNaviFollowerTest {
    @Test
    fun theBuiltInNaviTakesTwoWaypointsAtATime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val follower: RouteFollower = PleosNavi(context) {}

        assertEquals(RouteAbility(maxWaypoints = 2, acceptsWholePath = false), follower.ability)
    }
}
