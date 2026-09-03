package com.iykyk.facecollage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.facecollage.data.PersonIdentity
import com.iykyk.facecollage.data.Track
import com.iykyk.facecollage.pipeline.IdentityClusterer
import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.VideoPipeline
import com.iykyk.facecollage.pipeline.cosineSimilarity
import com.iykyk.facecollage.pipeline.meanNormalized
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic, not a pass/fail check.
 *
 * Answers one question: when two identities are NOT merged, how far apart are they really?
 * A near-miss just under the threshold is inherent embedding noise. A wide gap would mean
 * the per-track embedding averaging is losing information and deserves a look.
 *
 * Also reports whether the temporal-exclusion constraint blocked the merge, since that
 * would make the threshold irrelevant for that pair.
 */
@RunWith(AndroidJUnit4::class)
class ClusterGapDiagnosticTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reportSeparationBetweenUnmergedIdentities() = runBlocking {
        val config = PipelineConfig()
        val pipeline = VideoPipeline(context, config)

        // only the clip carrying ground truth, to stay inside the time box
        val sample = SampleVideos.all().firstOrNull { it.expected != null } ?: return@runBlocking

        val tracked = pipeline.buildTracks(sample.uri)
        val identities = IdentityClusterer(config).cluster(tracked.tracks)

        Log.i(TAG, "${sample.name}: ${identities.size} identities, threshold=${config.identityMergeThreshold}")
        for (identity in identities) {
            Log.i(
                TAG,
                "  id=${identity.id} tracks=${identity.tracks.size} appearances=${identity.appearanceCount} " +
                    "span=%.2f-%.2fs".format(
                        identity.tracks.first().startMs / 1000.0,
                        identity.tracks.last().endMs / 1000.0,
                    ),
            )
        }

        Log.i(TAG, "--- pairwise separation between identities that were NOT merged ---")
        val pairs = mutableListOf<Triple<Float, String, Boolean>>()
        for (i in identities.indices) {
            for (j in i + 1 until identities.size) {
                val a = identities[i]
                val b = identities[j]
                val similarity = averageLinkage(a, b, config)
                val blockedByOverlap = a.tracks.any { ta -> b.tracks.any { tb -> ta.overlapsInTime(tb) } }
                val margin = config.identityMergeThreshold - similarity
                pairs += Triple(
                    similarity,
                    "id${a.id}(${a.appearanceCount}app) vs id${b.id}(${b.appearanceCount}app): " +
                        "similarity=%.4f margin=%.4f overlap=%s".format(similarity, margin, blockedByOverlap),
                    blockedByOverlap,
                )
            }
        }

        pairs.sortedByDescending { it.first }.take(10).forEach { Log.i(TAG, "  ${it.second}") }

        val closest = pairs.filter { !it.third }.maxByOrNull { it.first }
        if (closest != null) {
            Log.i(
                TAG,
                "CLOSEST NON-OVERLAPPING PAIR similarity=%.4f threshold=%.2f shortfall=%.4f".format(
                    closest.first, config.identityMergeThreshold, config.identityMergeThreshold - closest.first,
                ),
            )
        }
    }

    /** Mirrors IdentityClusterer: average linkage over per-track embeddings. */
    private fun averageLinkage(a: PersonIdentity, b: PersonIdentity, config: PipelineConfig): Float {
        val ea = a.tracks.map { trackEmbedding(it, config) }
        val eb = b.tracks.map { trackEmbedding(it, config) }
        var total = 0f
        for (x in ea) for (y in eb) total += cosineSimilarity(x, y)
        return total / (ea.size * eb.size)
    }

    private fun trackEmbedding(track: Track, config: PipelineConfig) = meanNormalized(
        track.detections
            .sortedByDescending { it.rawQuality }
            .take(config.trackEmbeddingTopK)
            .map { it.embedding }
    )

    private companion object {
        const val TAG = "ClusterGap"
    }
}
