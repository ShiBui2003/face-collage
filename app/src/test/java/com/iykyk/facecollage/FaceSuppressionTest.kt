package com.iykyk.facecollage

import com.iykyk.facecollage.data.BoxF
import com.iykyk.facecollage.data.FaceAttributes
import com.iykyk.facecollage.data.FaceCandidate
import com.iykyk.facecollage.pipeline.suppressOverlappingFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSuppressionTest {

    private val threshold = 0.3f

    private fun candidate(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        sharpness: Float = 2000f,
        yaw: Float = 0f,
    ) = FaceCandidate(
        box = BoxF(left, top, right, bottom),
        attributes = FaceAttributes(
            eulerX = 0f,
            eulerY = yaw,
            eulerZ = 0f,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            smiling = 0.5f,
            sharpness = sharpness,
        ),
    )

    @Test
    fun `boxes on different faces are all kept`() {
        val left = candidate(0f, 200f, 350f, 700f)
        val right = candidate(400f, 200f, 700f, 700f)

        val kept = suppressOverlappingFaces(listOf(left, right), FRAME_H, threshold)

        assertEquals(2, kept.size)
    }

    @Test
    fun `two stacked boxes collapse to the sharper one`() {
        val blurry = candidate(60f, 240f, 400f, 700f, sharpness = 500f)
        val sharp = candidate(70f, 250f, 390f, 690f, sharpness = 3000f)

        val kept = suppressOverlappingFaces(listOf(blurry, sharp), FRAME_H, threshold)

        assertEquals(1, kept.size)
        assertSame(sharp, kept.single())
    }

    @Test
    fun `a more frontal box beats a turned one of equal sharpness`() {
        val turned = candidate(60f, 240f, 400f, 700f, yaw = 40f)
        val frontal = candidate(70f, 250f, 390f, 690f, yaw = 2f)

        assertSame(frontal, suppressOverlappingFaces(listOf(turned, frontal), FRAME_H, threshold).single())
    }

    /**
     * The exact geometry observed on a real clip: five ML Kit boxes over two people in a
     * split-screen frame, three stacked on the left face and two on the right. Same-face
     * overlaps measured 0.41-0.49, different-face overlaps 0.00-0.05.
     */
    @Test
    fun `five stacked boxes over two real faces collapse to two`() {
        val leftFace = listOf(
            candidate(3f, 212f, 413f, 766f),
            candidate(67f, 291f, 367f, 604f),
            candidate(67f, 395f, 373f, 726f),
        )
        val rightFace = listOf(
            candidate(379f, 263f, 765f, 779f),
            candidate(388f, 356f, 698f, 662f),
        )

        val kept = suppressOverlappingFaces(leftFace + rightFace, FRAME_H, threshold)

        assertEquals(2, kept.size)
        // one survivor from each side of the split screen
        assertTrue(kept.count { it.box.centerX < 360f } == 1)
        assertTrue(kept.count { it.box.centerX >= 360f } == 1)
    }

    @Test
    fun `single and empty inputs pass straight through`() {
        val one = candidate(0f, 0f, 100f, 100f)
        assertEquals(listOf(one), suppressOverlappingFaces(listOf(one), FRAME_H, threshold))
        assertEquals(emptyList<FaceCandidate>(), suppressOverlappingFaces(emptyList(), FRAME_H, threshold))
    }

    @Test
    fun `boxes overlapping just under the threshold are both kept`() {
        // IoU deliberately below 0.3: adjacent faces that merely touch must not be merged
        val a = candidate(0f, 0f, 300f, 400f)
        val b = candidate(180f, 0f, 480f, 400f)

        val iou = a.box.intersectionOverUnion(b.box)
        assertTrue("fixture IoU was $iou, expected below threshold", iou < threshold)
        assertEquals(2, suppressOverlappingFaces(listOf(a, b), FRAME_H, threshold).size)
    }

    private companion object {
        const val FRAME_H = 1280
    }
}
