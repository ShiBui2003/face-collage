package com.iykyk.facecollage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.facecollage.pipeline.IdentityClusterer
import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.VideoPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tuning harness, not a pass/fail check.
 *
 * Detection and embedding run once per clip; the resulting tracks are then re-clustered at
 * every candidate identityMergeThreshold. The point is to pick a value that is stable
 * across all three clips rather than one that happens to nail Sample 1, and to leave a
 * recorded basis for the README's tuning section.
 */
@RunWith(AndroidJUnit4::class)
class ThresholdSweepTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sweepIdentityMergeThreshold() = runBlocking {
        val base = PipelineConfig()
        val pipeline = VideoPipeline(context, base)
        val thresholds = generateSequence(0.40f) { it + 0.04f }.takeWhile { it <= 0.845f }.toList()

        for (sample in SampleVideos.all()) {
            val tracked = pipeline.buildTracks(sample.uri)
            Log.i(TAG, "==== ${sample.name}: ${tracked.tracks.size} tracks, ${tracked.facesDetected} faces ====")
            sample.expected?.let { Log.i(TAG, "  ground truth: ${it.identities} people, ${it.appearancesPerIdentity.sum()} appearances") }

            for (threshold in thresholds) {
                val identities = IdentityClusterer(base.copy(identityMergeThreshold = threshold))
                    .cluster(tracked.tracks)
                val counts = identities.map { it.appearanceCount }.sorted().reversed()
                val fours = counts.count { it == 4 }
                Log.i(
                    TAG,
                    "  %s threshold=%.2f people=%-3d appearances=%-3d withFour=%-2d counts=%s".format(
                        sample.name, threshold, identities.size, counts.sum(), fours, counts,
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "Sweep"
    }
}
