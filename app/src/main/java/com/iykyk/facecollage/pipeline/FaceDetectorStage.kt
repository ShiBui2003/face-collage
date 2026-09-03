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
        return faces.mapNotNull { face -> toCandidate(face, bitmap) }
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
