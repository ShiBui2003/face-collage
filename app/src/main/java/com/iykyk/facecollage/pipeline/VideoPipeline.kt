package com.iykyk.facecollage.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.iykyk.facecollage.data.BoxF
import com.iykyk.facecollage.data.CollageResult
import com.iykyk.facecollage.data.DetectedFace
import com.iykyk.facecollage.data.PersonIdentity
import com.iykyk.facecollage.data.PersonResult
import com.iykyk.facecollage.data.ProcessingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * The whole flow, top to bottom, in one readable place:
 * sample frames -> detect faces -> embed -> track -> cluster into people -> pick a shot each.
 *
 * Nothing here knows anything about any particular video.
 */
class VideoPipeline(
    private val context: Context,
    private val config: PipelineConfig = PipelineConfig(),
) {

    private val extractor = FrameExtractor(config)
    private val tracker = Tracker(config)
    private val clusterer = IdentityClusterer(config)
    private val scorer = RepresentativeFrameScorer(config)

    suspend fun run(uri: Uri, onProgress: (ProcessingState.Working) -> Unit): CollageResult =
        withContext(Dispatchers.Default) {
            val durationMs = extractor.durationMs(context, uri)
            require(durationMs > 0) { "That video could not be read." }

            val frames = detectAndEmbed(uri, durationMs, onProgress)
            val facesDetected = frames.sumOf { it.size }

            onProgress(working(ProcessingState.Stage.CLUSTERING, "Working out who is who", null))
            coroutineContext.ensureActive()
            val identities = clusterer.cluster(tracker.buildTracks(frames))

            onProgress(working(ProcessingState.Stage.SCORING, "Picking everyone's best shot", null))
            val people = identities.map { identity ->
                coroutineContext.ensureActive()
                PersonResult(
                    identityId = identity.id,
                    appearanceCount = identity.appearanceCount,
                    portrait = portraitFor(uri, identity),
                    appearances = identity.appearances,
                )
            }

            CollageResult(
                people = people,
                collage = null,
                videoDurationMs = durationMs,
                framesAnalysed = frames.size,
                facesDetected = facesDetected,
            )
        }

    /** One pass over the video: every sampled frame becomes a list of embedded faces. */
    private suspend fun detectAndEmbed(
        uri: Uri,
        durationMs: Long,
        onProgress: (ProcessingState.Working) -> Unit,
    ): List<List<DetectedFace>> {
        val planned = extractor.plannedFrameCount(durationMs).coerceAtLeast(1)
        val frames = mutableListOf<List<DetectedFace>>()
        // Captured so the non-suspend frame callback can still honour cancellation.
        val callerContext = coroutineContext

        FaceDetectorStage(config).use { detector ->
            TfliteFaceEmbedder(context).use { embedder ->
                var thrown: Throwable? = null
                extractor.forEachSampledFrame(context, uri) { frame ->
                    if (thrown != null) return@forEachSampledFrame
                    try {
                        callerContext.ensureActive()
                        frames += embedFaces(detector, embedder, frame)
                        onProgress(
                            working(
                                ProcessingState.Stage.DETECTING,
                                "Finding faces",
                                (frame.index + 1).toFloat() / planned,
                            )
                        )
                    } catch (t: Throwable) {
                        // forEachSampledFrame is not suspend, so surface cancellation after the walk.
                        thrown = t
                    }
                }
                thrown?.let { throw it }
            }
        }
        return frames
    }

    private fun embedFaces(
        detector: FaceDetectorStage,
        embedder: FaceEmbedder,
        frame: VideoFrame,
    ): List<DetectedFace> = detector.detect(frame.bitmap).mapNotNull { candidate ->
        // A modest margin for the model, unlike the generous crop used for the collage tile.
        val cropBox = candidate.box.expand(config.embeddingCropExpansion, frame.bitmap.width, frame.bitmap.height)
        val crop = frame.bitmap.cropTo(cropBox) ?: return@mapNotNull null
        val embedding = try {
            embedder.embed(crop)
        } finally {
            crop.recycle()
        }
        DetectedFace(
            frameIndex = frame.index,
            timestampMs = frame.timestampMs,
            box = candidate.box,
            embedding = embedding,
            attributes = candidate.attributes,
            frameWidth = frame.bitmap.width,
            frameHeight = frame.bitmap.height,
        )
    }

    /**
     * Re-extracts the chosen frame at full resolution and crops generously around the face.
     * Detection ran on a downscaled frame, so the box is scaled back up first; cropping the
     * small frame instead would produce exactly the low-resolution tiles the brief warns against.
     */
    private fun portraitFor(uri: Uri, identity: PersonIdentity): Bitmap {
        val best = scorer.pickBest(identity.detections)
            ?: error("identity ${identity.id} has no detections")

        val full = extractor.frameAt(context, uri, best.timestampMs)
            ?: error("could not re-read frame at ${best.timestampMs}ms")

        val scale = full.width.toFloat() / best.frameWidth
        val scaledBox = BoxF(
            best.box.left * scale,
            best.box.top * scale,
            best.box.right * scale,
            best.box.bottom * scale,
        )
        val generous = scaledBox.expand(config.faceCropExpansion, full.width, full.height)
        val portrait = full.cropTo(generous) ?: full
        if (portrait !== full) full.recycle()
        return portrait
    }

    private fun working(stage: ProcessingState.Stage, label: String, fraction: Float?) =
        ProcessingState.Working(stage, label, fraction?.coerceIn(0f, 1f))
}
