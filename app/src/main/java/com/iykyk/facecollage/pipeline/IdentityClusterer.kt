package com.iykyk.facecollage.pipeline

import com.iykyk.facecollage.data.Appearance
import com.iykyk.facecollage.data.PersonIdentity
import com.iykyk.facecollage.data.Track

/**
 * Stage 2 of appearance counting: merge tracks belonging to one person, however far
 * apart in time, then collapse each identity's tracks into visible segments.
 *
 * Agglomerative with average linkage over cosine similarity, plus one hard constraint:
 * tracks that overlap in time can never be the same person. That constraint is what keeps
 * two people who share a frame from collapsing into a single identity.
 */
class IdentityClusterer(private val config: PipelineConfig = PipelineConfig()) {

    fun cluster(tracks: List<Track>): List<PersonIdentity> {
        if (tracks.isEmpty()) return emptyList()

        val embeddings = tracks.map(::trackEmbedding)
        val clusters = tracks.indices.map { mutableListOf(it) }.toMutableList()

        // ponytail: O(n^3) overall for n tracks, which is tens at most for a 30 s clip.
        // Swap for a similarity heap if clip lengths ever grow by an order of magnitude.
        while (clusters.size > 1) {
            var bestSimilarity = -1f
            var mergeInto = -1
            var mergeFrom = -1

            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    if (overlapInTime(clusters[i], clusters[j], tracks)) continue
                    val similarity = averageLinkage(clusters[i], clusters[j], embeddings)
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        mergeInto = i
                        mergeFrom = j
                    }
                }
            }

            if (mergeInto < 0 || bestSimilarity < config.identityMergeThreshold) break
            clusters[mergeInto] += clusters[mergeFrom]
            clusters.removeAt(mergeFrom)
        }

        return clusters
            .map { indices -> indices.map { tracks[it] }.sortedBy { it.startMs } }
            .sortedBy { it.first().startMs }
            .mapIndexed { id, memberTracks ->
                PersonIdentity(id = id, tracks = memberTracks, appearances = toAppearances(memberTracks))
            }
    }

    /**
     * A track is represented by the mean of its best-quality detections rather than all of
     * them: averaging in the blurry tail of a track is the fastest way to blur two people together.
     */
    private fun trackEmbedding(track: Track): FloatArray = meanNormalized(
        track.detections
            .sortedByDescending { it.rawQuality }
            .take(config.trackEmbeddingTopK)
            .map { it.embedding }
    )

    /** One person cannot be in two places at once, so any temporal overlap forbids the merge. */
    private fun overlapInTime(a: List<Int>, b: List<Int>, tracks: List<Track>): Boolean =
        a.any { i -> b.any { j -> tracks[i].overlapsInTime(tracks[j]) } }

    private fun averageLinkage(a: List<Int>, b: List<Int>, embeddings: List<FloatArray>): Float {
        var total = 0f
        for (i in a) for (j in b) total += cosineSimilarity(embeddings[i], embeddings[j])
        return total / (a.size * b.size)
    }

    /**
     * Collapse chronologically ordered tracks into appearances. Two tracks closer than
     * [PipelineConfig.appearanceCoalesceGapMs] are one appearance that briefly broke up,
     * not two separate times on screen.
     */
    private fun toAppearances(tracks: List<Track>): List<Appearance> {
        val appearances = mutableListOf<Appearance>()
        var ids = mutableListOf(tracks.first().id)
        var start = tracks.first().startMs
        var end = tracks.first().endMs

        for (track in tracks.drop(1)) {
            if (track.startMs - end <= config.appearanceCoalesceGapMs) {
                ids += track.id
                end = maxOf(end, track.endMs)
            } else {
                appearances += Appearance(start, end, ids)
                ids = mutableListOf(track.id)
                start = track.startMs
                end = track.endMs
            }
        }
        appearances += Appearance(start, end, ids)
        return appearances
    }
}
