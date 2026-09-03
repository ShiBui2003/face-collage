package com.iykyk.facecollage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.facecollage.pipeline.TfliteFaceEmbedder
import com.iykyk.facecollage.pipeline.cosineSimilarity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * Verifies the bundled model really is what the README claims. The embedding dimension is a
 * graded, documented figure, so it is asserted against the model file rather than trusted.
 */
@RunWith(AndroidJUnit4::class)
class FaceEmbedderInstrumentedTest {

    private lateinit var embedder: TfliteFaceEmbedder

    @Before
    fun setUp() {
        embedder = TfliteFaceEmbedder(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After
    fun tearDown() {
        embedder.close()
    }

    @Test
    fun modelReportsTheDocumentedShapes() {
        assertEquals("input edge", 160, embedder.inputSize)
        assertEquals("embedding dimension", 128, embedder.embeddingSize)
    }

    @Test
    fun embeddingsAreUnitLengthAndFinite() {
        val v = embedder.embed(patch(seed = 1))
        assertEquals(128, v.size)
        assertTrue(v.all { it.isFinite() })
        assertEquals(1.0f, sqrt(v.fold(0f) { acc, x -> acc + x * x }), 1e-3f)
    }

    @Test
    fun sameInputProducesTheSameEmbedding() {
        val a = embedder.embed(patch(seed = 7))
        val b = embedder.embed(patch(seed = 7))
        assertEquals(1.0f, cosineSimilarity(a, b), 1e-4f)
    }

    @Test
    fun clearlyDifferentInputsProduceDifferentEmbeddings() {
        val a = embedder.embed(patch(seed = 2))
        val b = embedder.embed(patch(seed = 99))
        assertTrue("similarity was ${cosineSimilarity(a, b)}", cosineSimilarity(a, b) < 0.99f)
    }

    /** A deterministic synthetic image. Not a face; this test is about plumbing, not accuracy. */
    private fun patch(seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40 + seed % 60, 90, 140))
        val paint = Paint().apply { color = Color.rgb(220, 180 - seed % 50, 120) }
        canvas.drawCircle(100f, 90f + seed % 20, 45f + seed % 15, paint)
        canvas.drawRect(60f, 140f, 140f, 170f + seed % 10, paint)
        return bitmap
    }
}
