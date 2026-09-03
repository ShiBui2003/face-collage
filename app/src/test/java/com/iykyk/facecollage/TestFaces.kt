package com.iykyk.facecollage

import com.iykyk.facecollage.data.BoxF
import com.iykyk.facecollage.data.DetectedFace
import com.iykyk.facecollage.data.FaceAttributes
import com.iykyk.facecollage.pipeline.l2Normalized

/**
 * Synthetic face fixtures. No video, no ML Kit, no TFLite: these tests prove the
 * tracking / clustering / scoring logic in isolation, which is where the appearance
 * counting can actually be reasoned about.
 */

const val DIM = 128
const val FRAME_W = 720
const val FRAME_H = 1280
const val SAMPLE_MS = 125L

/** Deterministic pseudo-random unit vector, stable for a given person id. */
private fun randomUnit(seed: Int): FloatArray {
    var s = (seed + 1) * 7919
    val v = FloatArray(DIM)
    for (i in 0 until DIM) {
        s = s * 1103515245 + 12345
        v[i] = (((s ushr 8) and 0xFFFF) / 65535f) - 0.5f
    }
    return l2Normalized(v)
}

/**
 * An embedding for [person]. [noise] blends in an unrelated direction, so a larger
 * value means a less similar observation of the same person.
 */
fun personEmbedding(person: Int, noise: Float = 0.0f, variant: Int = 0): FloatArray {
    val base = randomUnit(person)
    if (noise <= 0f) return base
    val perturb = randomUnit(10_000 + person * 97 + variant)
    return l2Normalized(FloatArray(DIM) { base[it] * (1f - noise) + perturb[it] * noise })
}

/** A face box of roughly realistic size, centred at ([cx], [cy]). */
fun boxAt(cx: Float, cy: Float, size: Float = 160f): BoxF =
    BoxF(cx - size / 2f, cy - size * 0.6f, cx + size / 2f, cy + size * 0.6f)

fun face(
    frame: Int,
    person: Int,
    box: BoxF = boxAt(360f, 640f),
    noise: Float = 0.05f,
    variant: Int = frame,
    sharpness: Float = 120f,
    eulerX: Float = 0f,
    eulerY: Float = 0f,
    eulerZ: Float = 0f,
    leftEye: Float? = 0.9f,
    rightEye: Float? = 0.9f,
    smile: Float? = 0.7f,
    timestampMs: Long = frame * SAMPLE_MS,
    frameWidth: Int = FRAME_W,
    frameHeight: Int = FRAME_H,
): DetectedFace = DetectedFace(
    frameIndex = frame,
    timestampMs = timestampMs,
    box = box,
    embedding = personEmbedding(person, noise, variant),
    attributes = FaceAttributes(
        eulerX = eulerX,
        eulerY = eulerY,
        eulerZ = eulerZ,
        leftEyeOpen = leftEye,
        rightEyeOpen = rightEye,
        smiling = smile,
        sharpness = sharpness,
    ),
    frameWidth = frameWidth,
    frameHeight = frameHeight,
)
