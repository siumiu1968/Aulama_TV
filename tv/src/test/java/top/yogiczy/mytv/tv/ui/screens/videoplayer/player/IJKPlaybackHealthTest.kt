package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IJKPlaybackHealthTest {
    @Test
    fun `very low output fps is unhealthy even while audio clock advances`() {
        assertTrue(
            isPlaybackHealthUnhealthy(
                outputFps = 4f,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = true,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `zero output fps is unhealthy after output telemetry was observed`() {
        assertTrue(
            isPlaybackHealthUnhealthy(
                outputFps = 0f,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = true,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `unavailable output fps does not fail healthy hardware decoding`() {
        assertFalse(
            isPlaybackHealthUnhealthy(
                outputFps = 0f,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = false,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `non finite output telemetry does not fail healthy hardware decoding`() {
        assertFalse(
            isPlaybackHealthUnhealthy(
                outputFps = Float.NaN,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = false,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `decode stall is unhealthy after decode telemetry was observed`() {
        assertTrue(
            isPlaybackHealthUnhealthy(
                outputFps = 0f,
                decodeFps = 0f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = false,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `stalled playback position is unhealthy without fps telemetry`() {
        assertTrue(
            isPlaybackHealthUnhealthy(
                outputFps = 0f,
                decodeFps = 0f,
                progressDelta = 0L,
                minimumFps = 8f,
                hasObservedOutputFps = false,
                hasObservedDecodeFps = false,
            ),
        )
    }

    @Test
    fun `stalled live timeline stays healthy while decode and output fps are healthy`() {
        assertFalse(
            isPlaybackHealthUnhealthy(
                outputFps = 25f,
                decodeFps = 25f,
                progressDelta = 0L,
                minimumFps = 16f,
                hasObservedOutputFps = true,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `healthy decode fps beats an unavailable live timeline and output counter`() {
        assertFalse(
            isPlaybackHealthUnhealthy(
                outputFps = 0f,
                decodeFps = 31.25f,
                progressDelta = 250L,
                minimumFps = 16f,
                hasObservedOutputFps = false,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `normal fps and advancing position remain healthy`() {
        assertFalse(
            isPlaybackHealthUnhealthy(
                outputFps = 25f,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = true,
                hasObservedDecodeFps = true,
            ),
        )
    }

    @Test
    fun `severe sustained lip sync drift is unhealthy`() {
        assertTrue(
            isPlaybackHealthUnhealthy(
                outputFps = 25f,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = true,
                hasObservedDecodeFps = true,
                avDifferenceSeconds = -0.85f,
            ),
        )
    }

    @Test
    fun `small clock correction remains healthy`() {
        assertFalse(
            isPlaybackHealthUnhealthy(
                outputFps = 25f,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = true,
                hasObservedDecodeFps = true,
                avDifferenceSeconds = -0.04f,
            ),
        )
    }

    @Test
    fun `temporary clock correction does not discard a playing route`() {
        assertFalse(
            isPlaybackHealthUnhealthy(
                outputFps = 25f,
                decodeFps = 25f,
                progressDelta = 3_000L,
                minimumFps = 8f,
                hasObservedOutputFps = true,
                hasObservedDecodeFps = true,
                avDifferenceSeconds = 0.44f,
            ),
        )
    }
}
