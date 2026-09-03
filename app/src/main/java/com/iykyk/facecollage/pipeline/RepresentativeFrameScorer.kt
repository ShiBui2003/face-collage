package com.iykyk.facecollage.pipeline

import com.iykyk.facecollage.data.DetectedFace

/**
 * Picks the shot that best represents a person: frontal, sharp, eyes open, pleasant,
 * reasonably large, and not clipped by the frame edge.
 */
class RepresentativeFrameScorer(private val config: PipelineConfig = PipelineConfig()) {

    fun pickBest(candidates: List<DetectedFace>): DetectedFace? {
        if (candidates.isEmpty()) return null
        var minSharpness = Float.MAX_VALUE
        var maxSharpness = -Float.MAX_VALUE
        for (candidate in candidates) {
            val s = candidate.attributes.sharpness
            if (s < minSharpness) minSharpness = s
            if (s > maxSharpness) maxSharpness = s
        }
        return candidates.maxByOrNull { score(it, minSharpness, maxSharpness) }
    }

    /**
     * Sharpness is normalised across [minSharpness]..[maxSharpness] rather than an absolute
     * scale, because raw Laplacian variance shifts with lighting and face size. A dim clip
     * must still be able to rank its own best frame first.
     */
    fun score(face: DetectedFace, minSharpness: Float, maxSharpness: Float): Float {
        val span = maxSharpness - minSharpness
        val sharpness =
            if (span <= 0f) 1f else ((face.attributes.sharpness - minSharpness) / span).coerceIn(0f, 1f)
        val size = (face.relativeSize / TARGET_RELATIVE_SIZE).coerceIn(0f, 1f)

        val score = config.weightFrontality * face.attributes.frontality +
            config.weightSharpness * sharpness +
            config.weightEyesOpen * face.attributes.eyesOpen +
            config.weightSmile * face.attributes.smile +
            config.weightSize * size

        return if (face.isFullyVisible) score else score * config.clippedFacePenalty
    }

    private companion object {
        /** A face this tall relative to the frame is already big enough for a good tile. */
        const val TARGET_RELATIVE_SIZE = 0.25f
    }
}
