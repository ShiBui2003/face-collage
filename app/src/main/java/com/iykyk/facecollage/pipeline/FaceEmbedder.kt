package com.iykyk.facecollage.pipeline

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.sqrt

/** Turns a face crop into a fixed-length vector. One implementation today; the seam keeps it swappable. */
interface FaceEmbedder : Closeable {
    val embeddingSize: Int
    fun embed(faceCrop: Bitmap): FloatArray
}

/**
 * FaceNet running on TensorFlow Lite.
 *
 * Model: facenet.tflite, 160x160x3 RGB float input, 128-d output.
 * Source: shubham0204/FaceRecognition_With_FaceNet_Android (Apache-2.0),
 * weights from nyoki-mtl/keras-facenet (MIT). See README.
 *
 * Input and output shapes are read from the model itself rather than hardcoded, so a
 * swapped model cannot silently produce garbage.
 */
class TfliteFaceEmbedder(
    context: Context,
    modelAsset: String = DEFAULT_MODEL_ASSET,
    threads: Int = 4,
) : FaceEmbedder {

    private val interpreter = Interpreter(
        loadMappedModel(context, modelAsset),
        Interpreter.Options().apply { numThreads = threads },
    )

    /** Square input edge, read from the model (160 for FaceNet). */
    val inputSize: Int = interpreter.getInputTensor(0).shape()[1]

    override val embeddingSize: Int = interpreter.getOutputTensor(0).shape()[1]

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(inputSize * inputSize * CHANNELS * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
    private val pixels = IntArray(inputSize * inputSize)

    override fun embed(faceCrop: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(faceCrop, inputSize, inputSize, true)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (scaled !== faceCrop) scaled.recycle()

        writeStandardized(pixels)

        val output = Array(1) { FloatArray(embeddingSize) }
        interpreter.run(inputBuffer, output)
        // FaceNet embeddings are compared by cosine similarity, so normalise once here
        // and every downstream comparison is well defined.
        return l2Normalized(output[0])
    }

    /**
     * FaceNet expects per-image standardisation ("prewhitening"), not a fixed 0..1 scale.
     * Getting this wrong degrades embeddings subtly rather than obviously, so it is done
     * exactly as the reference implementation does.
     */
    private fun writeStandardized(pixels: IntArray) {
        var sum = 0.0
        var sumSq = 0.0
        val values = FloatArray(pixels.size * CHANNELS)
        var v = 0
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            values[v++] = r
            values[v++] = g
            values[v++] = b
            sum += r + g + b
            sumSq += r.toDouble() * r + g.toDouble() * g + b.toDouble() * b
        }
        val n = values.size
        val mean = sum / n
        val variance = (sumSq / n) - mean * mean
        val std = max(sqrt(max(variance, 0.0)), 1.0 / sqrt(n.toDouble())).toFloat()

        inputBuffer.rewind()
        val meanF = mean.toFloat()
        for (value in values) inputBuffer.putFloat((value - meanF) / std)
        inputBuffer.rewind()
    }

    override fun close() = interpreter.close()

    private companion object {
        const val DEFAULT_MODEL_ASSET = "facenet.tflite"
        const val CHANNELS = 3

        fun loadMappedModel(context: Context, asset: String) =
            context.assets.openFd(asset).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    stream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
    }
}
