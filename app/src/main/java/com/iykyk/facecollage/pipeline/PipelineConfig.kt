package com.iykyk.facecollage.pipeline

import kotlin.math.ceil
import kotlin.math.max

/**
 * Every tunable in the pipeline, in one place.
 *
 * Nothing here is derived from any particular video. Defaults were tuned against
 * Sample 1 and sanity-checked on Samples 2 and 3; see README for the trade-off.
 */
data class PipelineConfig(
    // ---- frame sampling ----
    /** ~8 fps. Fast enough to catch a ~1.4 s appearance many times, cheap enough to finish quickly. */
    val sampleIntervalMs: Long = 125L,
    /** Frames are downscaled to this width before detection. */
    val maxFrameWidth: Int = 720,

    // ---- detection quality gate: implements "clearly visible" ----
    /** Faces shorter than this fraction of frame height are too small to identify reliably. */
    val minFaceHeightFraction: Float = 0.07f,
    /** Laplacian-variance floor. This is what makes blurred whip-pan frames count for nobody. */
    val minSharpness: Float = 15f,
    /** Beyond this yaw the embedding is unreliable. */
    val maxUsableYaw: Float = 50f,
    /**
     * Two boxes overlapping by more than this are the same face, so only the better one is kept.
     *
     * Measured twice. The obvious duplicates on crowded frames overlap by 0.41-0.49 against
     * 0.00-0.11 for boxes on different faces. A second, subtler population survived at 0.30:
     * duplicates that overlap by only 0.245-0.290, which then formed a parallel track on the
     * same person and could never be merged away, because two tracks overlapping in time are
     * barred from joining. Across all three clips boxes on different faces never exceeded
     * 0.198, so 0.22 sits in the gap and removes that second population too.
     */
    val nmsIouThreshold: Float = 0.22f,

    // ---- tracking (frame to frame) ----
    val trackEmbeddingWeight: Float = 0.60f,
    val trackIouWeight: Float = 0.25f,
    val trackCenterWeight: Float = 0.15f,
    /** Below this cosine similarity two detections are never the same track. */
    val minTrackCosine: Float = 0.50f,
    /** Max centre movement per elapsed sampled frame, as a multiple of face width. Handheld video moves a lot. */
    val maxCenterMovePerFrame: Float = 1.2f,
    /** A track survives this many consecutive sampled frames with no match before it closes. */
    val maxGapFrames: Int = 3,
    /**
     * The brief counts an appearance as a continuous VISIBLE segment. A face flashing up for
     * two or three sampled frames at a scene cut is not that, so segments shorter than this
     * are discarded rather than counted.
     */
    val minVisibleSegmentMs: Long = 500L,

    // ---- identity clustering (whole video) ----
    /**
     * Merge two track clusters while their average cosine similarity is at least this.
     *
     * Chosen from a sweep over all three clips, not from whichever value scored best on the
     * one clip with a known answer. Below ~0.46 samples 2 and 3 over-merge (distinct people
     * collapse into one 7- or 8-appearance blob); above ~0.54 sample 1 fragments into extra
     * singleton identities. 0.48-0.52 is the only band where all three are simultaneously
     * stable, so this sits in the middle of it.
     */
    val identityMergeThreshold: Float = 0.50f,
    /** A track is represented by the mean of its best-quality detections, not all of them. */
    val trackEmbeddingTopK: Int = 5,
    /**
     * Two segments of one person closer than this are one appearance that briefly broke up,
     * not two. MUST stay below [trackBreakGapMs]; see the init block.
     */
    val appearanceCoalesceGapMs: Long = 250L,

    // ---- representative frame scoring ----
    val weightFrontality: Float = 0.35f,
    val weightSharpness: Float = 0.25f,
    val weightEyesOpen: Float = 0.20f,
    val weightSmile: Float = 0.10f,
    val weightSize: Float = 0.10f,
    /** Multiplier applied when the face touches the frame edge (clipped). */
    val clippedFacePenalty: Float = 0.5f,
    /**
     * Multiplier applied when the source frame held more than one face. A generous crop around
     * one person in a two-shot drags in the other, which then appears in someone else's tile as
     * well; a solo frame is strongly preferred where the person has one available.
     */
    val sharedFramePenalty: Float = 0.45f,

    /**
     * Margin around the face box fed to the embedding model. Modest on purpose: the model
     * expects a face-filling crop. This is NOT the collage crop, which is deliberately generous.
     */
    val embeddingCropExpansion: Float = 1.25f,

    // ---- collage ----
    /** Face box is expanded by this factor for the tile crop. Never crop tight to the box. */
    val faceCropExpansion: Float = 3.0f,
) {

    /** How long the tracker tolerates losing a face before it closes the track. */
    val trackBreakGapMs: Long get() = maxGapFrames * sampleIntervalMs

    /** How many sampled frames a track must span to count as a visible segment. */
    val minTrackDetections: Int
        get() = max(2, ceil(minVisibleSegmentMs.toDouble() / sampleIntervalMs).toInt())

    init {
        // The tracker splits a track when it loses a face for longer than trackBreakGapMs.
        // If clustering then re-merged segments across a WIDER gap than that, it would
        // systematically undo the tracker's decision and weld distinct appearances together.
        // Coalescing must always be the stricter of the two.
        require(appearanceCoalesceGapMs < trackBreakGapMs) {
            "appearanceCoalesceGapMs (" + appearanceCoalesceGapMs + "ms) must be strictly less " +
                "than trackBreakGapMs (" + trackBreakGapMs + "ms), otherwise clustering re-merges " +
                "segments the tracker deliberately split"
        }
        require(sampleIntervalMs > 0) { "sampleIntervalMs must be positive" }
        require(maxGapFrames >= 1) { "maxGapFrames must be at least 1" }
    }
}
