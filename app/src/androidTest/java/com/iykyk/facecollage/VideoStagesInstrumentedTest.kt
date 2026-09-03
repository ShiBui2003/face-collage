package com.iykyk.facecollage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.facecollage.pipeline.FaceDetectorStage
import com.iykyk.facecollage.pipeline.FrameExtractor
import com.iykyk.facecollage.pipeline.PipelineConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Android-side stages actually work on the real clips: frames decode, they are
 * distinct from one another, and ML Kit finds faces that survive the visibility gate.
 * Generic over whatever clips are present; asserts nothing clip-specific.
 */
@RunWith(AndroidJUnit4::class)
class VideoStagesInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = PipelineConfig()
    private val extractor = FrameExtractor(config)

    @Test
    fun everySampleDecodesToManyDistinctFrames() {
        val samples = SampleVideos.all()
        assertTrue("no sample clips found", samples.isNotEmpty())

        for (sample in samples) {
            val duration = extractor.durationMs(context, sample.uri)
            assertTrue("${sample.name} duration was $duration", duration > 1_000)

            var decoded = 0
            var lastWidth = 0
            extractor.forEachSampledFrame(context, sample.uri) { frame ->
                decoded++
                lastWidth = frame.bitmap.width
            }
            assertTrue("${sample.name} decoded only $decoded frames", decoded > 100)
            assertTrue("${sample.name} frame width was $lastWidth", lastWidth in 1..config.maxFrameWidth)
        }
    }

    @Test
    fun mlKitFindsFacesThatSurviveTheVisibilityGate() = runBlocking {
        val detector = FaceDetectorStage(config)
        try {
            for (sample in SampleVideos.all()) {
                var framesWithFaces = 0
                var totalFaces = 0
                var maxInOneFrame = 0

                extractor.forEachSampledFrame(context, sample.uri) { frame ->
                    val faces = runBlocking { detector.detect(frame.bitmap) }
                    if (faces.isNotEmpty()) framesWithFaces++
                    totalFaces += faces.size
                    if (faces.size > maxInOneFrame) maxInOneFrame = faces.size
                }

                android.util.Log.i(
                    "VideoStages",
                    "${sample.name}: framesWithFaces=$framesWithFaces totalFaces=$totalFaces maxInOneFrame=$maxInOneFrame",
                )
                assertTrue("${sample.name} found no faces at all", totalFaces > 0)
                assertTrue("${sample.name} had faces in only $framesWithFaces frames", framesWithFaces > 10)
            }
        } finally {
            detector.close()
        }
    }
}
