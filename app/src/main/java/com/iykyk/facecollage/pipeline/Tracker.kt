package com.iykyk.facecollage.pipeline

import com.iykyk.facecollage.data.DetectedFace
import com.iykyk.facecollage.data.Track
import kotlin.math.hypot

/**
 * Stage 1 of appearance counting: stitch per-frame detections into short continuous tracks.
 *
 * Matching combines embedding similarity with box overlap and centre motion, because
 * neither alone survives handheld video: position alone swaps identities when two people
 * cross, and embeddings alone happily join two people who never shared a frame.
 */
class Tracker(private val config: PipelineConfig = PipelineConfig()) {

    private class OpenTrack(val id: Int, first: DetectedFace, var lastFrame: Int) {
        val detections = mutableListOf(first)
        private val sum = first.embedding.copyOf()

        /** Running mean of every detection so far, so one blurry frame cannot hijack the track. */
        var mean: FloatArray = l2Normalized(sum)
            private set

        fun add(detection: DetectedFace, frameIndex: Int) {
            detections += detection
            for (i in sum.indices) sum[i] += detection.embedding[i]
            mean = l2Normalized(sum)
            lastFrame = frameIndex
        }
    }

    /**
     * [frames] must be in temporal order; the outer index is the sampled frame ordinal.
     * Returns closed tracks, chronologically, with flicker removed.
     */
    fun buildTracks(frames: List<List<DetectedFace>>): List<Track> {
        val open = mutableListOf<OpenTrack>()
        val finished = mutableListOf<OpenTrack>()
        var nextId = 0

        frames.forEachIndexed { frameIndex, detections ->
            // Retire tracks that have gone unmatched for too long.
            val stale = open.filter { frameIndex - it.lastFrame > config.maxGapFrames }
            finished += stale
            open.removeAll(stale.toSet())

            // Score every legal (track, detection) pairing, then take them cheapest-first.
            // ponytail: greedy assignment, fine for the handful of faces in frame at once.
            // Swap in Hungarian if frames ever carry 10+ simultaneous faces.
            val pairs = ArrayList<Triple<Float, OpenTrack, DetectedFace>>()
            for (track in open) {
                for (detection in detections) {
                    val cost = matchCost(track, detection, frameIndex) ?: continue
                    pairs += Triple(cost, track, detection)
                }
            }
            pairs.sortBy { it.first }

            val claimedTracks = HashSet<Int>()
            val claimedDetections = HashSet<DetectedFace>()
            for ((_, track, detection) in pairs) {
                if (!claimedTracks.add(track.id)) continue
                if (!claimedDetections.add(detection)) continue
                track.add(detection, frameIndex)
            }

            for (detection in detections) {
                if (detection !in claimedDetections) {
                    open += OpenTrack(nextId++, detection, frameIndex)
                }
            }
        }
        finished += open

        return finished
            .filter { it.detections.size >= config.minTrackDetections }
            .sortedBy { it.detections.first().timestampMs }
            .map { Track(id = it.id, detections = it.detections.toList()) }
    }

    /** Lower is better. Null means the pairing is impossible and must not be considered. */
    private fun matchCost(track: OpenTrack, detection: DetectedFace, frameIndex: Int): Float? {
        val similarity = cosineSimilarity(track.mean, detection.embedding)
        if (similarity < config.minTrackCosine) return null

        val last = track.detections.last().box
        val elapsed = (frameIndex - track.lastFrame).coerceAtLeast(1)
        val distance = hypot(detection.box.centerX - last.centerX, detection.box.centerY - last.centerY)
        val allowedMove = config.maxCenterMovePerFrame * maxOf(last.width, detection.box.width) * elapsed
        if (distance > allowedMove) return null

        val iou = last.intersectionOverUnion(detection.box)
        val diagonal = hypot(detection.frameWidth.toFloat(), detection.frameHeight.toFloat())

        return config.trackEmbeddingWeight * (1f - similarity) +
            config.trackIouWeight * (1f - iou) +
            config.trackCenterWeight * (distance / diagonal)
    }
}
