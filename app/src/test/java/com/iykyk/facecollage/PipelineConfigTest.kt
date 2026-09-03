package com.iykyk.facecollage

import com.iykyk.facecollage.pipeline.PipelineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the relationship between tracking and clustering constants.
 *
 * These two were independently tunable and silently drifted apart: the tracker split a
 * track after 375ms while clustering re-merged across 500ms, so distinct appearances were
 * being welded back together. Encoding the invariant here stops that recurring.
 */
class PipelineConfigTest {

    @Test
    fun `default config coalesces more strictly than the tracker breaks tracks`() {
        val config = PipelineConfig()
        assertTrue(
            "coalesce ${config.appearanceCoalesceGapMs}ms must be under break ${config.trackBreakGapMs}ms",
            config.appearanceCoalesceGapMs < config.trackBreakGapMs,
        )
    }

    @Test
    fun `a coalesce gap at or above the track break threshold is rejected`() {
        // 3 frames x 125ms = 375ms break threshold; 375 is not strictly less than 375
        val error = assertThrows(IllegalArgumentException::class.java) {
            PipelineConfig(sampleIntervalMs = 125L, maxGapFrames = 3, appearanceCoalesceGapMs = 375L)
        }
        assertTrue(error.message!!.contains("strictly less"))

        assertThrows(IllegalArgumentException::class.java) {
            PipelineConfig(sampleIntervalMs = 125L, maxGapFrames = 3, appearanceCoalesceGapMs = 500L)
        }
    }

    @Test
    fun `changing the sample interval keeps the invariant enforced`() {
        // Doubling the interval doubles the break threshold, so a previously illegal value becomes legal
        val config = PipelineConfig(sampleIntervalMs = 250L, maxGapFrames = 3, appearanceCoalesceGapMs = 500L)
        assertEquals(750L, config.trackBreakGapMs)
        assertTrue(config.appearanceCoalesceGapMs < config.trackBreakGapMs)
    }

    @Test
    fun `minTrackDetections is derived from the minimum visible segment`() {
        val config = PipelineConfig(sampleIntervalMs = 125L, minVisibleSegmentMs = 500L)
        assertEquals(4, config.minTrackDetections)

        // a coarser sampling interval needs fewer frames to cover the same duration
        assertEquals(2, PipelineConfig(sampleIntervalMs = 250L, minVisibleSegmentMs = 500L).minTrackDetections)
    }

    @Test
    fun `minTrackDetections never drops below two`() {
        // a single detection can never establish a continuous segment, whatever the durations say
        val config = PipelineConfig(sampleIntervalMs = 125L, minVisibleSegmentMs = 1L)
        assertEquals(2, config.minTrackDetections)
    }

    @Test
    fun `a two to three frame blip is below the visible-segment minimum`() {
        val config = PipelineConfig()
        // the transition fragments observed on real clips spanned 2-3 sampled frames
        assertTrue("3 frames must not qualify", 3 < config.minTrackDetections)
    }
}
