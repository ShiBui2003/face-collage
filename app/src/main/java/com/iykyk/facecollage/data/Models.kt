package com.iykyk.facecollage.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Axis-aligned box in source-frame pixel coordinates.
 *
 * Deliberately not android.graphics.RectF: keeping this plain Kotlin is what lets the
 * tracking, clustering and scoring logic run as ordinary JVM unit tests with no emulator.
 */
data class BoxF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = max(0f, width) * max(0f, height)

    fun intersectionOverUnion(other: BoxF): Float {
        val interW = max(0f, min(right, other.right) - max(left, other.left))
        val interH = max(0f, min(bottom, other.bottom) - max(top, other.top))
        val inter = interW * interH
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }

    /** Expand by [factor] around the center, clamped to a frame of the given size. */
    fun expand(factor: Float, frameWidth: Int, frameHeight: Int): BoxF {
        val halfW = width * factor / 2f
        val halfH = height * factor / 2f
        return BoxF(
            left = max(0f, centerX - halfW),
            top = max(0f, centerY - halfH),
            right = min(frameWidth.toFloat(), centerX + halfW),
            bottom = min(frameHeight.toFloat(), centerY + halfH),
        )
    }

    /** True when the box sits fully inside the frame with at least [margin] px to spare. */
    fun isFullyVisibleIn(frameWidth: Int, frameHeight: Int, margin: Float = 0f): Boolean =
        left >= margin &&
            top >= margin &&
            right <= frameWidth - margin &&
            bottom <= frameHeight - margin
}

/**
 * Per-detection face qualities. Angles are ML Kit Euler angles in degrees.
 * Probabilities are 0..1, or null when ML Kit could not compute them.
 */
data class FaceAttributes(
    val eulerX: Float,
    val eulerY: Float,
    val eulerZ: Float,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val smiling: Float?,
    /** Raw Laplacian variance over the face region. Scale depends on lighting, so normalise before comparing. */
    val sharpness: Float,
) {
    /** 1.0 looking straight at the camera, falling to 0.0 at [MAX_USABLE_ANGLE] on any axis. */
    val frontality: Float
        get() {
            val yaw = 1f - (abs(eulerY) / MAX_USABLE_ANGLE).coerceIn(0f, 1f)
            val roll = 1f - (abs(eulerZ) / MAX_USABLE_ANGLE).coerceIn(0f, 1f)
            val pitch = 1f - (abs(eulerX) / MAX_USABLE_ANGLE).coerceIn(0f, 1f)
            return yaw * 0.5f + pitch * 0.3f + roll * 0.2f
        }

    /** Both eyes open, treating "unknown" as neutral rather than closed. */
    val eyesOpen: Float
        get() = (leftEyeOpen ?: NEUTRAL) * (rightEyeOpen ?: NEUTRAL)

    val smile: Float get() = smiling ?: NEUTRAL

    companion object {
        const val MAX_USABLE_ANGLE = 45f
        private const val NEUTRAL = 0.5f
    }
}

/**
 * One face found in one sampled frame.
 *
 * Holds no Bitmap on purpose: a 30 s clip yields hundreds of detections, and keeping crops
 * alive would blow the heap. The representative frames are re-extracted by timestamp at
 * the very end, when only a handful are needed.
 */
class DetectedFace(
    val frameIndex: Int,
    val timestampMs: Long,
    val box: BoxF,
    val embedding: FloatArray,
    val attributes: FaceAttributes,
    val frameWidth: Int,
    val frameHeight: Int,
    /**
     * How many faces survived detection in this frame. A generously cropped tile taken from a
     * split-screen or two-shot pulls in the neighbour, so a solo frame makes a better portrait.
     */
    val facesInFrame: Int = 1,
) {
    /** Face height as a fraction of frame height. Bigger faces make better collage tiles. */
    val relativeSize: Float get() = box.height / frameHeight.toFloat()

    val isFullyVisible: Boolean get() = box.isFullyVisibleIn(frameWidth, frameHeight)

    /** Cheap pre-normalisation quality used to pick which detections represent a track. */
    val rawQuality: Float get() = attributes.frontality * attributes.sharpness
}

/** A face found in a frame, before the embedding model has run on it. */
data class FaceCandidate(
    val box: BoxF,
    val attributes: FaceAttributes,
)

/** A continuous run of detections believed to be one person, within one pass of the video. */
data class Track(
    val id: Int,
    val detections: List<DetectedFace>,
) {
    val startMs: Long get() = detections.first().timestampMs
    val endMs: Long get() = detections.last().timestampMs

    fun overlapsInTime(other: Track): Boolean = startMs <= other.endMs && other.startMs <= endMs
}

/** One continuous visible segment for a person. This is the unit the brief counts. */
data class Appearance(
    val startMs: Long,
    val endMs: Long,
    val trackIds: List<Int>,
)

/** A person: every track merged into one identity, plus the segments they appeared in. */
data class PersonIdentity(
    val id: Int,
    val tracks: List<Track>,
    val appearances: List<Appearance>,
) {
    val appearanceCount: Int get() = appearances.size
    val detections: List<DetectedFace> get() = tracks.flatMap { it.detections }
}
