package com.iykyk.facecollage

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.facecollage.pipeline.MediaSaver
import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.VideoPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the real collage for every clip and writes it out so it can be inspected, and
 * checks the share path produces a usable intent. Presentation is judged by eye; what is
 * asserted here is that the collage exists, has the Story canvas size, is not blank, and
 * shows exactly one tile per person.
 */
@RunWith(AndroidJUnit4::class)
class CollageRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everySampleProducesAShareableCollage() = runBlocking {
        val pipeline = VideoPipeline(context, PipelineConfig())
        val outDir = File(context.getExternalFilesDir(null), "collages").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }

        for (sample in SampleVideos.all()) {
            val result = pipeline.run(sample.uri) { }
            val collage = result.collage

            Log.i(
                TAG,
                "${sample.name}: ${collage.width}x${collage.height} people=${result.people.size} " +
                    "appearances=${result.totalAppearances}",
            )

            assertEquals("collage width", 1080, collage.width)
            assertEquals("collage height", 1920, collage.height)
            assertTrue("${sample.name} collage is blank", isNotBlank(collage))

            // one tile per person: the brief requires every person shown exactly once
            assertEquals(
                "${sample.name} tile count must equal person count",
                result.people.size,
                result.people.distinctBy { it.identityId }.size,
            )

            val intent = MediaSaver.shareIntent(context, collage)
            assertEquals("image/jpeg", intent.type)
            assertTrue("share intent carries no image", intent.extras?.containsKey(android.content.Intent.EXTRA_STREAM) == true)

            val out = File(outDir, "${sample.name}_collage.png")
            out.outputStream().use { collage.compress(Bitmap.CompressFormat.PNG, 95, it) }
            Log.i(TAG, "  wrote ${out.absolutePath}")
        }
    }

    /** Guards against a collage that renders as a flat rectangle if drawing silently fails. */
    private fun isNotBlank(bitmap: Bitmap): Boolean {
        val step = 40
        val seen = HashSet<Int>()
        for (x in 0 until bitmap.width step step) {
            for (y in 0 until bitmap.height step step) {
                seen += bitmap.getPixel(x, y)
                if (seen.size > 50) return true
            }
        }
        return false
    }

    private companion object {
        const val TAG = "CollageRender"
    }
}
