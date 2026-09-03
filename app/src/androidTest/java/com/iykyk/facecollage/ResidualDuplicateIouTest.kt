package com.iykyk.facecollage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.facecollage.data.Track
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
 * Measures the box overlap between tracks that coexist in time, split by whether they look
 * like the same face. Concurrent tracks with a high embedding similarity are residual
 * duplicates that slipped under the NMS threshold; concurrent tracks with low similarity
 * are genuinely different people sharing a frame.
 *
 * If those two populations separate as cleanly as the original NMS measurement did
 * (0.41-0.49 vs 0.00-0.11) then a lower threshold is safe. If they do not, it is not.
 */
@RunWith(AndroidJUnit4::class)
class ResidualDuplicateIouTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measureOverlapBetweenConcurrentTracks() = runBlocking {
        val config = PipelineConfig()
        val pipeline = VideoPipeline(context, config)

        for (sample in SampleVideos.all()) {
            val tracks = pipeline.buildTracks(sample.uri).tracks
            val embeddings = tracks.map { trackEmbedding(it, config) }

            val sameFaceIous = mutableListOf<Float>()
            val differentFaceIous = mutableListOf<Float>()

            for (i in tracks.indices) {
                for (j in i + 1 until tracks.size) {
                    if (!tracks[i].overlapsInTime(tracks[j])) continue

                    val similarity = cosineSimilarity(embeddings[i], embeddings[j])

                    // box overlap on the frames where both tracks are actually present
                    val byFrame = tracks[j].detections.associateBy { it.frameIndex }
                    val ious = tracks[i].detections.mapNotNull { a ->
                        byFrame[a.frameIndex]?.let { b -> a.box.intersectionOverUnion(b.box) }
                    }
                    if (ious.isEmpty()) continue

                    val target = if (similarity >= config.identityMergeThreshold) sameFaceIous else differentFaceIous
                    target += ious

                    Log.i(
                        TAG,
                        "%s track%d/track%d sim=%.3f sharedFrames=%d iou min=%.3f mean=%.3f max=%.3f %s".format(
                            sample.name, tracks[i].id, tracks[j].id, similarity, ious.size,
                            ious.min(), ious.average().toFloat(), ious.max(),
                            if (similarity >= config.identityMergeThreshold) "LIKELY-DUPLICATE" else "different-people",
                        ),
                    )
                }
            }

            Log.i(TAG, "==== ${sample.name} SUMMARY ====")
            report("  same-face (likely duplicate)", sameFaceIous)
            report("  different-face (real pairs) ", differentFaceIous)
        }
    }

    private fun report(label: String, ious: List<Float>) {
        if (ious.isEmpty()) {
            Log.i(TAG, "$label: none")
            return
        }
        val sorted = ious.sorted()
        fun pct(p: Double) = sorted[(sorted.size * p).toInt().coerceIn(0, sorted.size - 1)]
        Log.i(
            TAG,
            "%s: n=%d min=%.3f p10=%.3f p50=%.3f p90=%.3f max=%.3f".format(
                label, sorted.size, sorted.first(), pct(0.10), pct(0.50), pct(0.90), sorted.last(),
            ),
        )
    }

    private fun trackEmbedding(track: Track, config: PipelineConfig) = meanNormalized(
        track.detections
            .sortedByDescending { it.rawQuality }
            .take(config.trackEmbeddingTopK)
            .map { it.embedding }
    )

    private companion object {
        const val TAG = "DupIoU"
    }
}
