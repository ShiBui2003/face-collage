package com.iykyk.facecollage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.facecollage.data.BoxF
import com.iykyk.facecollage.pipeline.FrameExtractor
import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.laplacianVariance
import com.google.android.gms.tasks.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Diagnostic, not a pass/fail check. Runs ML Kit with NO visibility gate and dumps what the
 * gate would be deciding about, so Phase 4 tunes against a measured cause rather than a guess.
 */
@RunWith(AndroidJUnit4::class)
class DetectionDiagnosticsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = PipelineConfig()
    private val extractor = FrameExtractor(config)
    private val tag = "FaceDiag"

    @Test
    fun dumpUngatedDetectionCharacteristics() {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                // deliberately permissive: we want to see what a tighter gate would remove
                .setMinFaceSize(0.02f)
                .build()
        )

        try {
            for (sample in SampleVideos.all()) {
                var raw = 0
                var failSize = 0
                var failYaw = 0
                var failSharp = 0
                var passAll = 0
                var crowdedFrames = 0
                var dumped = 0
                val relHeights = mutableListOf<Float>()
                val sharpnesses = mutableListOf<Float>()

                extractor.forEachSampledFrame(context, sample.uri) { frame ->
                    val faces = Tasks.await(detector.process(InputImage.fromBitmap(frame.bitmap, 0)))
                    raw += faces.size
                    if (faces.size >= 3) crowdedFrames++

                    val rows = faces.map { face ->
                        val box = BoxF(
                            face.boundingBox.left.toFloat(), face.boundingBox.top.toFloat(),
                            face.boundingBox.right.toFloat(), face.boundingBox.bottom.toFloat(),
                        )
                        val relH = box.height / frame.bitmap.height
                        val sharp = laplacianVariance(frame.bitmap, box)
                        relHeights += relH
                        sharpnesses += sharp

                        val tooSmall = relH < config.minFaceHeightFraction
                        val tooTurned = abs(face.headEulerAngleY) > config.maxUsableYaw
                        val tooBlurry = sharp < config.minSharpness
                        if (tooSmall) failSize++
                        if (tooTurned) failYaw++
                        if (tooBlurry) failSharp++
                        if (!tooSmall && !tooTurned && !tooBlurry) passAll++

                        val edge = box.left <= 1f || box.top <= 1f ||
                            box.right >= frame.bitmap.width - 1f || box.bottom >= frame.bitmap.height - 1f
                        "relH=%.3f w=%.0f h=%.0f yaw=%.0f sharp=%.0f edge=%s eyes=%s smile=%s %s".format(
                            relH, box.width, box.height, face.headEulerAngleY, sharp, edge,
                            face.leftEyeOpenProbability?.let { "%.2f".format(it) } ?: "null",
                            face.smilingProbability?.let { "%.2f".format(it) } ?: "null",
                            if (tooSmall || tooTurned || tooBlurry) "REJECT" else "keep",
                        )
                    }

                    if (faces.size >= 3 && dumped < 12) {
                        dumped++
                        Log.i(tag, "${sample.name} t=${frame.timestampMs}ms faces=${faces.size}")
                        rows.forEach { Log.i(tag, "    $it") }
                    }
                }

                relHeights.sort()
                sharpnesses.sort()
                fun pct(list: List<Float>, p: Double) =
                    if (list.isEmpty()) 0f else list[(list.size * p).toInt().coerceIn(0, list.size - 1)]

                Log.i(
                    tag,
                    "${sample.name} SUMMARY raw=$raw pass=$passAll failSize=$failSize " +
                        "failYaw=$failYaw failSharp=$failSharp crowdedFrames(>=3)=$crowdedFrames",
                )
                Log.i(
                    tag,
                    "${sample.name} relHeight p10=%.3f p50=%.3f p90=%.3f | sharpness p10=%.0f p50=%.0f p90=%.0f".format(
                        pct(relHeights, 0.10), pct(relHeights, 0.50), pct(relHeights, 0.90),
                        pct(sharpnesses, 0.10), pct(sharpnesses, 0.50), pct(sharpnesses, 0.90),
                    ),
                )
            }
        } finally {
            detector.close()
        }
    }
}
