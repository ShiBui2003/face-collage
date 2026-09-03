package com.iykyk.facecollage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.facecollage.data.BoxF
import com.iykyk.facecollage.pipeline.FrameExtractor
import com.iykyk.facecollage.pipeline.PipelineConfig
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Diagnostic, not a pass/fail check.
 *
 * Answers two questions at once:
 *  1. Are the extra detections duplicates of one face, or separate people? (pairwise IoU)
 *  2. Are they an artefact of downscaling? (same frame detected at 720px and at native size)
 */
@RunWith(AndroidJUnit4::class)
class FrameDumpTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val extractor = FrameExtractor(PipelineConfig())

    @Test
    fun compareScaledAndNativeDetection() {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setMinFaceSize(0.02f)
                .build()
        )
        val outDir = File(context.getExternalFilesDir(null), "diag").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }

        try {
            for (sample in SampleVideos.all()) {
                var bestCount = 0
                var bestMs = 0L
                var bestFrame: Bitmap? = null
                var bestFaces: List<Face> = emptyList()
                var crowded = 0
                var crowdedNativeTotal = 0
                var crowdedScaledTotal = 0

                extractor.forEachSampledFrame(context, sample.uri) { frame ->
                    val faces = Tasks.await(detector.process(InputImage.fromBitmap(frame.bitmap, 0)))

                    // Whenever the scaled frame looks crowded, re-check the same instant at native size.
                    if (faces.size >= 3) {
                        crowded++
                        crowdedScaledTotal += faces.size
                        extractor.frameAt(context, sample.uri, frame.timestampMs)?.let { native ->
                            val nativeFaces =
                                Tasks.await(detector.process(InputImage.fromBitmap(native, 0)))
                            crowdedNativeTotal += nativeFaces.size
                            Log.i(
                                TAG,
                                "${sample.name} t=${frame.timestampMs}ms scaled=${faces.size}" +
                                    " (${frame.bitmap.width}x${frame.bitmap.height})" +
                                    " native=${nativeFaces.size} (${native.width}x${native.height})",
                            )
                            native.recycle()
                        }
                    }

                    if (faces.size > bestCount) {
                        bestCount = faces.size
                        bestMs = frame.timestampMs
                        bestFaces = faces
                        bestFrame?.recycle()
                        bestFrame = frame.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    }
                }

                Log.i(
                    TAG,
                    "${sample.name} CROWD SUMMARY frames=$crowded scaledFaces=$crowdedScaledTotal " +
                        "nativeFaces=$crowdedNativeTotal",
                )

                val frame = bestFrame ?: continue
                val boxes = bestFaces.map {
                    BoxF(
                        it.boundingBox.left.toFloat(), it.boundingBox.top.toFloat(),
                        it.boundingBox.right.toFloat(), it.boundingBox.bottom.toFloat(),
                    )
                }
                for (i in boxes.indices) {
                    for (j in i + 1 until boxes.size) {
                        Log.i(TAG, "  ${sample.name} IoU $i/$j = %.3f".format(boxes[i].intersectionOverUnion(boxes[j])))
                    }
                }

                save(frame, bestFaces, File(outDir, "${sample.name}_scaled_${bestMs}ms_${bestFaces.size}faces.png"))

                extractor.frameAt(context, sample.uri, bestMs)?.let { native ->
                    val nativeFaces = Tasks.await(detector.process(InputImage.fromBitmap(native, 0)))
                    save(native, nativeFaces, File(outDir, "${sample.name}_native_${bestMs}ms_${nativeFaces.size}faces.png"))
                    native.recycle()
                }

                frame.recycle()
            }
        } finally {
            detector.close()
        }
    }

    private fun save(source: Bitmap, faces: List<Face>, out: File) {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = annotated.width / 120f
            color = Color.MAGENTA
        }
        val label = Paint().apply {
            color = Color.YELLOW
            textSize = annotated.width / 18f
            isFakeBoldText = true
        }
        faces.forEachIndexed { index, face ->
            canvas.drawRect(android.graphics.RectF(face.boundingBox), stroke)
            canvas.drawText(
                "$index",
                face.boundingBox.left.toFloat() + 6f,
                face.boundingBox.top.toFloat() + label.textSize,
                label,
            )
        }
        out.outputStream().use { annotated.compress(Bitmap.CompressFormat.PNG, 90, it) }
        annotated.recycle()
    }

    private companion object {
        const val TAG = "FrameDump"
    }
}
