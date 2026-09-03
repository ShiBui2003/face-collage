package com.iykyk.facecollage

import com.iykyk.facecollage.data.BoxF
import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.RepresentativeFrameScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RepresentativeFrameScorerTest {

    private val scorer = RepresentativeFrameScorer(PipelineConfig())

    @Test
    fun `prefers the frontal sharp eyes-open shot over a blurry profile`() {
        val blurryProfile = face(frame = 0, person = 1, sharpness = 20f, eulerY = 40f, leftEye = 0.2f, rightEye = 0.2f)
        val good = face(frame = 1, person = 1, sharpness = 200f, eulerY = 2f, leftEye = 0.95f, rightEye = 0.95f)

        assertSame(good, scorer.pickBest(listOf(blurryProfile, good)))
    }

    @Test
    fun `prefers eyes open when everything else is equal`() {
        val closed = face(frame = 0, person = 1, leftEye = 0.05f, rightEye = 0.05f)
        val open = face(frame = 1, person = 1, leftEye = 0.95f, rightEye = 0.95f)

        assertSame(open, scorer.pickBest(listOf(closed, open)))
    }

    @Test
    fun `prefers a smile when everything else is equal`() {
        val neutral = face(frame = 0, person = 1, smile = 0.05f)
        val smiling = face(frame = 1, person = 1, smile = 0.95f)

        assertSame(smiling, scorer.pickBest(listOf(neutral, smiling)))
    }

    @Test
    fun `penalises a face clipped by the frame edge`() {
        // clipped face is otherwise better: sharper and more frontal
        val clipped = face(
            frame = 0,
            person = 1,
            box = BoxF(-20f, 600f, 140f, 792f),
            sharpness = 220f,
            eulerY = 0f,
        )
        val whole = face(frame = 1, person = 1, box = boxAt(360f, 640f), sharpness = 150f, eulerY = 8f)

        assertSame(whole, scorer.pickBest(listOf(clipped, whole)))
    }

    @Test
    fun `prefers a larger face when other attributes match`() {
        val small = face(frame = 0, person = 1, box = boxAt(360f, 640f, size = 90f))
        val large = face(frame = 1, person = 1, box = boxAt(360f, 640f, size = 260f))

        assertSame(large, scorer.pickBest(listOf(small, large)))
    }

    @Test
    fun `a single candidate is returned as-is`() {
        val only = face(frame = 0, person = 1, sharpness = 16f, eulerY = 44f)
        assertSame(only, scorer.pickBest(listOf(only)))
    }

    @Test
    fun `no candidates yields null rather than throwing`() {
        assertNull(scorer.pickBest(emptyList()))
    }

    @Test
    fun `sharpness is normalised within the candidate set, not against an absolute scale`() {
        // A dim clip where every frame has low absolute sharpness must still rank its own best first.
        val dim = listOf(
            face(frame = 0, person = 1, sharpness = 18f),
            face(frame = 1, person = 1, sharpness = 30f),
            face(frame = 2, person = 1, sharpness = 24f),
        )

        assertEquals(1, scorer.pickBest(dim)!!.frameIndex)
    }
}
