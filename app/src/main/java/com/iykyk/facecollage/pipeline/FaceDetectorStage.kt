package com.iykyk.facecollage.pipeline

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.facecollage.data.BoxF
import com.iykyk.facecollage.data.FaceAttributes
import com.iykyk.facecollage.data.FaceCandidate
import com.google.android.gms.tasks.Tasks
import java.io.Closeable
import kotlin.math.abs

/**
 * ML Kit face detection plus the "clearly visible" quality gate.
 *
 * The gate lives here, before embedding, for two reasons: it is the cheapest place to drop
 * a blurred whip-pan frame, and it means nothing downstream ever has to reason about
 * whether a detection was really visible. A face that fails the gate cannot start a track,
 * which is precisely how the brief's "blurred whip-pan passes count for nobody" is honoured.
 */
class FaceDetectorStage(private val config: PipelineConfig = PipelineConfig()) : Closeable {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(config.minFaceHeightFraction)
            .build()
    )

    /**
     * Detections that passed the visibility gate, in the frame's own pixel coordinates.
     * Blocking: the whole pipeline is sequential and already runs off the main thread,
     * so a suspend seam here would only add ceremony. Never call this on the main thread.
     */
    fun detect(bitmap: Bitmap): List<FaceCandidate> {
        val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
        val candidates = faces.mapNotNull { face -> toCandidate(face, bitmap) }
        return suppressOverlappingFaces(candidates, bitmap.height, config.nmsIouThreshold)
    }

    private fun toCandidate(face: Face, bitmap: Bitmap): FaceCandidate? {
        val box = BoxF(
            left = face.boundingBox.left.toFloat(),
            top = face.boundingBox.top.toFloat(),
            right = face.boundingBox.right.toFloat(),
            bottom = face.boundingBox.bottom.toFloat(),
        )

        if (box.height < config.minFaceHeightFraction * bitmap.height) return null
        if (abs(face.headEulerAngleY) > config.maxUsableYaw) return null

        val sharpness = laplacianVariance(bitmap, box)
        if (sharpness < config.minSharpness) return null

        return FaceCandidate(
            box = box,
            attributes = FaceAttributes(
                eulerX = face.headEulerAngleX,
                eulerY = face.headEulerAngleY,
                eulerZ = face.headEulerAngleZ,
                leftEyeOpen = face.leftEyeOpenProbability,
                rightEyeOpen = face.rightEyeOpenProbability,
                smiling = face.smilingProbability,
                sharpness = sharpness,
            ),
        )
    }

    override fun close() = detector.close()
}

/**
 * Greedy non-maximum suppression over a single frame's detections.
 *
 * ML Kit emits several stacked boxes for one face on downscaled frames. Left alone each
 * duplicate starts its own track and then its own identity, which roughly doubles both the
 * person count and the appearance count. Keeping only the best box per face is a
 * correctness fix, not a tuning knob.
 *
 * Pure by design so it can be unit tested on the JVM with no emulator.
 */
fun suppressOverlappingFaces(
    candidates: List<FaceCandidate>,
    frameHeight: Int,
    iouThreshold: Float,
): List<FaceCandidate> {
    if (candidates.size < 2) return candidates

    val kept = ArrayList<FaceCandidate>(candidates.size)
    for (candidate in candidates.sortedByDescending { it.quality(frameHeight) }) {
        val overlapsKept = kept.any { it.box.intersectionOverUnion(candidate.box) > iouThreshold }
        if (!overlapsKept) kept += candidate
    }
    return kept
}

/** Sharpness x frontality x relative size: the crispest, most frontal, largest box wins. */
private fun FaceCandidate.quality(frameHeight: Int): Float =
    attributes.sharpness * attributes.frontality * (box.height / frameHeight.toFloat())
