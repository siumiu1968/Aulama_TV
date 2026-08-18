package top.yogiczy.mytv.tv.ui.screens.main.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainContentPlaybackRecoveryPolicyTest {
    @Test
    fun `hard playback stalls force recovery`() {
        assertTrue(requiresImmediatePlaybackRecovery("first-frame-timeout"))
        assertTrue(requiresImmediatePlaybackRecovery("long-rebuffer"))
        assertTrue(requiresImmediatePlaybackRecovery("ijk-playback-stalled"))
        assertTrue(requiresImmediatePlaybackRecovery("ijk-decode-stalled"))
        assertTrue(requiresImmediatePlaybackRecovery("audio-underrun"))
    }

    @Test
    fun `statistical buffering only switches to a clearly better route`() {
        assertFalse(requiresImmediatePlaybackRecovery("stall-threshold"))
        assertFalse(requiresImmediatePlaybackRecovery("buffer-ratio:0.200"))
        assertFalse(requiresImmediatePlaybackRecovery("ijk-av-sync-drift"))
        assertFalse(requiresImmediatePlaybackRecovery("ijk-slow-rendering"))
        assertFalse(requiresImmediatePlaybackRecovery("slow-rendering"))
        assertFalse(requiresImmediatePlaybackRecovery("dropped-frames"))
    }

    @Test
    fun `first frame exhaustion changes route while a later hard stall reloads once`() {
        assertFalse(shouldReloadCurrentCandidate("first-frame-timeout"))
        assertTrue(shouldReloadCurrentCandidate("long-rebuffer"))
        assertTrue(shouldReloadCurrentCandidate("ijk-playback-stalled"))
        assertFalse(shouldReloadCurrentCandidate("ijk-slow-rendering"))
    }

    @Test
    fun `same candidate reload is bounded until stable playback restores it`() {
        val budget = PlaybackRecoveryBudget()

        assertTrue(budget.tryUseCandidateReload("route-a|direct"))
        assertFalse(budget.tryUseCandidateReload("route-a|direct"))
        assertTrue(budget.tryUseCandidateReload("route-b|direct"))

        budget.restoreCandidateReload("route-a|direct")
        assertTrue(budget.tryUseCandidateReload("route-a|direct"))
    }

    @Test
    fun `explicit retry resets all candidate reload budgets`() {
        val budget = PlaybackRecoveryBudget()
        assertTrue(budget.tryUseCandidateReload("route-a|direct"))
        assertTrue(budget.tryUseCandidateReload("route-b|direct"))

        budget.reset()

        assertTrue(budget.tryUseCandidateReload("route-a|direct"))
        assertTrue(budget.tryUseCandidateReload("route-b|direct"))
    }

    @Test
    fun `soft degradation notice is emitted once until stable playback restores it`() {
        val budget = SoftDegradationNoticeBudget()

        assertTrue(budget.tryNotify("route-a|direct"))
        assertFalse(budget.tryNotify("route-a|direct"))
        assertTrue(budget.tryNotify("route-b|direct"))

        budget.restore("route-a|direct")
        assertTrue(budget.tryNotify("route-a|direct"))

        budget.reset()
        assertTrue(budget.tryNotify("route-b|direct"))
    }

    @Test
    fun `route revisit is limited to one extra pass`() {
        assertTrue(canStartRouteRevisit(currentPass = 0, routeCount = 4))
        assertFalse(canStartRouteRevisit(currentPass = 1, routeCount = 4))
        assertFalse(canStartRouteRevisit(currentPass = 0, routeCount = 1))
    }

    @Test
    fun `route revisit waits until five seconds after the previous attempt`() {
        assertEquals(3_000L, routeRevisitDelayMs(lastAttemptAt = 5_000L, now = 7_000L))
        assertEquals(0L, routeRevisitDelayMs(lastAttemptAt = 5_000L, now = 30_000L))
    }
}
