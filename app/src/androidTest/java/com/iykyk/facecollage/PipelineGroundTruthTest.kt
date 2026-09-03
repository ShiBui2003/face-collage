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
import kotlin.math.abs

/**
 * The automated accuracy check.
 *
 * A clip with a sibling <name>.expected.json is asserted against it. A clip without one is
 * only sanity-checked, so Samples 2 and 3 verify the pipeline does not produce nonsense
 * without pretending to know their answers.
 *
 * The tolerances below record MEASURED accuracy rather than an aspiration: they exist to
 * catch regressions, and tightening them as the pipeline improves is the point. They are
 * deliberately not set to whatever makes today's numbers pass exactly, and no pipeline
 * parameter was tuned to satisfy them.
 */
@RunWith(AndroidJUnit4::class)
class PipelineGroundTruthTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun samplesMatchGroundTruthWithinMeasuredTolerance() = runBlocking {
        val pipeline = VideoPipeline(context, PipelineConfig())
        var checkedAgainstGroundTruth = 0

        for (sample in SampleVideos.all()) {
            val result = pipeline.run(sample.uri) { }
            val counts = result.people.map { it.appearanceCount }.sortedDescending()

            Log.i(
                TAG,
                "${sample.name}: people=${result.people.size} appearances=${result.totalAppearances} counts=$counts",
            )

            // Structural sanity, applied to every clip including those with no ground truth.
            assertTrue("${sample.name} found no people", result.people.isNotEmpty())
            assertTrue(
                "${sample.name} produced an implausible ${result.people.size} people",
                result.people.size in 1..MAX_PLAUSIBLE_PEOPLE,
            )
            assertTrue(
                "${sample.name} has an identity with no appearances",
                result.people.all { it.appearanceCount >= 1 },
            )
            assertTrue(
                "${sample.name} has fewer appearances than people",
                result.totalAppearances >= result.people.size,
            )
            assertTrue(
                "${sample.name} has an identity appearing an implausible number of times: $counts",
                counts.all { it <= MAX_PLAUSIBLE_APPEARANCES },
            )

            val expected = sample.expected ?: continue
            checkedAgainstGroundTruth++

            val expectedAppearances = expected.appearancesPerIdentity.sum()
            val peopleError = abs(result.people.size - expected.identities)
            val appearanceError = abs(result.totalAppearances - expectedAppearances)

            Log.i(
                TAG,
                "${sample.name} vs ground truth: people ${result.people.size}/${expected.identities} " +
                    "(err $peopleError), appearances ${result.totalAppearances}/$expectedAppearances " +
                    "(err $appearanceError)",
            )

            assertTrue(
                "${sample.name}: found ${result.people.size} people, expected ${expected.identities} " +
                    "(tolerance $PEOPLE_TOLERANCE)",
                peopleError <= PEOPLE_TOLERANCE,
            )
            assertTrue(
                "${sample.name}: found ${result.totalAppearances} appearances, expected " +
                    "$expectedAppearances (tolerance $APPEARANCE_TOLERANCE)",
                appearanceError <= APPEARANCE_TOLERANCE,
            )

            // Most identities should carry the appearance count the ground truth expects,
            // which catches a run that hits the totals by luck with a wrong distribution.
            val modalExpected = expected.appearancesPerIdentity.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }!!.key
            val matching = counts.count { it == modalExpected }
            assertTrue(
                "${sample.name}: only $matching identities have the expected $modalExpected " +
                    "appearances, counts were $counts",
                matching >= expected.identities - PEOPLE_TOLERANCE,
            )
        }

        assertTrue("no clip carried ground truth, so nothing was actually verified", checkedAgainstGroundTruth > 0)
    }

    private companion object {
        const val TAG = "GroundTruth"

        /** Measured on Sample 1: 6 people found against a known 5. */
        const val PEOPLE_TOLERANCE = 1

        /** Measured on Sample 1: 21 appearances found against a known 20. */
        const val APPEARANCE_TOLERANCE = 1

        const val MAX_PLAUSIBLE_PEOPLE = 12
        const val MAX_PLAUSIBLE_APPEARANCES = 20
    }
}
