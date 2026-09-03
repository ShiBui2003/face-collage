package com.iykyk.facecollage

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.VideoPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke test: proves the whole pipeline runs on real clips and produces people,
 * counts, segment ranges and portraits. Deliberately asserts only that the output is
 * structurally sane; ground-truth accuracy is Phase 4's job, in its own test.
 */
@RunWith(AndroidJUnit4::class)
class PipelineSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everySampleRunsEndToEnd() = runBlocking {
        val pipeline = VideoPipeline(context, PipelineConfig())

        for (sample in SampleVideos.all()) {
            val started = System.currentTimeMillis()
            val result = pipeline.run(sample.uri) { }
            val elapsed = System.currentTimeMillis() - started

            Log.i(TAG, "==== ${sample.name} ====")
            Log.i(
                TAG,
                "duration=${result.videoDurationMs}ms frames=${result.framesAnalysed} " +
                    "faces=${result.facesDetected} people=${result.people.size} " +
                    "appearances=${result.totalAppearances} elapsed=${elapsed}ms",
            )
            for (person in result.people) {
                val segments = person.appearances.joinToString(", ") {
                    "%.2f-%.2fs".format(it.startMs / 1000.0, it.endMs / 1000.0)
                }
                Log.i(
                    TAG,
                    "  person ${person.identityId}: ${person.appearanceCount} appearances " +
                        "[$segments] portrait=${person.portrait.width}x${person.portrait.height}",
                )
            }
            sample.expected?.let {
                Log.i(TAG, "  EXPECTED identities=${it.identities} counts=${it.appearancesPerIdentity}")
            }

            assertTrue("${sample.name} found no people", result.people.isNotEmpty())
            assertTrue(
                "${sample.name} produced fewer appearances than people",
                result.totalAppearances >= result.people.size,
            )
            for (person in result.people) {
                assertTrue(
                    "${sample.name} person ${person.identityId} portrait is tiny",
                    person.portrait.width > 80 && person.portrait.height > 80,
                )
            }
        }
    }

    private companion object {
        const val TAG = "PipelineSmoke"
    }
}
