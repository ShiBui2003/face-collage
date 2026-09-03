package com.iykyk.facecollage

import com.iykyk.facecollage.pipeline.cosineSimilarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fixture itself. If these fail, every other test in this module is
 * meaningless, because "same person" and "different person" would not be separable.
 */
class TestFacesSanityTest {

    @Test
    fun `same person across observations stays highly similar`() {
        val a = personEmbedding(person = 1, noise = 0.05f, variant = 0)
        val b = personEmbedding(person = 1, noise = 0.05f, variant = 9)
        assertTrue("same person similarity was ${cosineSimilarity(a, b)}", cosineSimilarity(a, b) > 0.9f)
    }

    @Test
    fun `different people are clearly separated`() {
        for (p in 0 until 5) {
            for (q in p + 1 until 5) {
                val s = cosineSimilarity(personEmbedding(p, 0.05f, 1), personEmbedding(q, 0.05f, 2))
                assertTrue("person $p vs $q similarity was $s", s < 0.5f)
            }
        }
    }

    @Test
    fun `embeddings are unit length`() {
        val v = personEmbedding(3, 0.2f, 4)
        val norm = kotlin.math.sqrt(v.fold(0f) { acc, x -> acc + x * x })
        assertEquals(1.0f, norm, 1e-4f)
    }
}
