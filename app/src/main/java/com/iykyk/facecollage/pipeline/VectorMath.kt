package com.iykyk.facecollage.pipeline

import kotlin.math.sqrt

/** Cosine similarity. Returns 0 for a zero-length vector rather than NaN. */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "embedding size mismatch: ${a.size} vs ${b.size}" }
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom <= 0f) 0f else dot / denom
}

/** Unit-length copy. A zero vector is returned unchanged. */
fun l2Normalized(v: FloatArray): FloatArray {
    var n = 0f
    for (x in v) n += x * x
    val norm = sqrt(n)
    if (norm <= 0f) return v.copyOf()
    return FloatArray(v.size) { v[it] / norm }
}

/** Element-wise mean of the given vectors, normalised to unit length. */
fun meanNormalized(vectors: List<FloatArray>): FloatArray {
    require(vectors.isNotEmpty()) { "cannot average zero vectors" }
    val out = FloatArray(vectors.first().size)
    for (v in vectors) for (i in out.indices) out[i] += v[i]
    for (i in out.indices) out[i] /= vectors.size
    return l2Normalized(out)
}
