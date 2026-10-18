package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.*
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute

class IptvRouteSessionPolicyTest {
    @Test
    fun manualFirstRouteSurvivesLearnedThirdRouteAndBackgroundProbes() {
        val policy = IptvRouteSessionPolicy()
        policy.beginSession(0)
        val routes = listOf(ChannelRoute("first"), ChannelRoute("second"), ChannelRoute("third"))
        val order = mergeRouteAttemptOrder(routes, listOf("third"), listOf(2, 1, 0), 0)
        val measured = reorderUnattemptedRoutesByProbe(
            routes, order, 0,
            listOf(IptvRouteProbeResult(2, IptvRouteProbeStatus.AVAILABLE, 10)),
            mapOf(0 to 40.0, 2 to 95.0),
        )
        assertEquals(0, measured.first())
        assertFalse(policy.allowsSoftSwitch(0))
        assertTrue(policy.allowsSoftSwitch(2))
    }

    @Test
    fun selectingActiveRouteStillEstablishesManualIntent() {
        val policy = IptvRouteSessionPolicy()
        policy.beginSession(null)
        assertTrue(policy.allowsSoftSwitch(2))
        policy.selectManual(2)
        assertFalse(policy.allowsSoftSwitch(2))
        policy.beginSession(null)
        assertTrue(policy.allowsSoftSwitch(2))
    }

}
