package com.iykyk.facecollage.pipeline

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
     * Two boxes overlapping by more than this are the same face, so only the better one is
     * kept. Measured on the sample clips: boxes on one face overlap by 0.41-0.49, boxes on
     * different faces by 0.00-0.11, so anything in 0.2-0.35 separates them cleanly.
     */
    val nmsIouThreshold: Float = 0.3f,

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
    /** Tracks shorter than this are flicker, not an appearance. */
    val minTrackDetections: Int = 2,

    // ---- identity clustering (whole video) ----
    /** Merge two track clusters while their average cosine similarity is at least this. */
    val identityMergeThreshold: Float = 0.62f,
    /** A track is represented by the mean of its best-quality detections, not all of them. */
    val trackEmbeddingTopK: Int = 5,
    /** Two segments of one person closer than this are one appearance, not two. */
    val appearanceCoalesceGapMs: Long = 500L,

    // ---- representative frame scoring ----
    val weightFrontality: Float = 0.35f,
    val weightSharpness: Float = 0.25f,
    val weightEyesOpen: Float = 0.20f,
    val weightSmile: Float = 0.10f,
    val weightSize: Float = 0.10f,
    /** Multiplier applied when the face touches the frame edge (clipped). */
    val clippedFacePenalty: Float = 0.5f,

    /**
     * Margin around the face box fed to the embedding model. Modest on purpose: the model
     * expects a face-filling crop. This is NOT the collage crop, which is deliberately generous.
     */
    val embeddingCropExpansion: Float = 1.25f,

    // ---- collage ----
    /** Face box is expanded by this factor for the tile crop. Never crop tight to the box. */
    val faceCropExpansion: Float = 3.0f,
)
