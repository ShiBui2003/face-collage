package com.iykyk.facecollage.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One sampled frame. The bitmap is owned by the extractor and recycled after the callback. */
class VideoFrame(val index: Int, val timestampMs: Long, val bitmap: Bitmap)

/**
 * Samples frames at a fixed interval using MediaMetadataRetriever.
 *
 * ponytail: MediaMetadataRetriever seeks per frame, which is slower than driving MediaCodec
 * against a Surface but is a fraction of the code. A 30 s clip at 8 fps stays well inside a
 * progress bar's patience. Move to MediaCodec only if clip lengths grow substantially.
 */
class FrameExtractor(private val config: PipelineConfig = PipelineConfig()) {

    /** Number of frames [forEachSampledFrame] will produce for a clip of [durationMs]. */
    fun plannedFrameCount(durationMs: Long): Int =
        if (durationMs <= 0) 0 else ((durationMs + config.sampleIntervalMs - 1) / config.sampleIntervalMs).toInt()

    fun durationMs(context: Context, uri: Uri): Long = withRetriever(context, uri) { it.durationMs() }

    /**
     * Walks the clip in order, handing each sampled frame to [onFrame]. The bitmap is
     * recycled once [onFrame] returns, so callers must not retain it.
     * Returns the number of frames actually decoded.
     */
    fun forEachSampledFrame(context: Context, uri: Uri, onFrame: (VideoFrame) -> Unit): Int =
        withRetriever(context, uri) { retriever ->
            val duration = retriever.durationMs()
            val (targetWidth, targetHeight) = retriever.targetSize()
            var index = 0
            var timestampMs = 0L
            while (timestampMs < duration) {
                val bitmap = retriever.frameAt(timestampMs, targetWidth, targetHeight)
                if (bitmap != null) {
                    try {
                        onFrame(VideoFrame(index, timestampMs, bitmap))
                    } finally {
                        bitmap.recycle()
                    }
                }
                index++
                timestampMs += config.sampleIntervalMs
            }
            index
        }

    /**
     * A single full-resolution frame, used to re-extract the handful of representative shots
     * once the pipeline knows which ones it wants. Caller owns the returned bitmap.
     */
    fun frameAt(context: Context, uri: Uri, timestampMs: Long): Bitmap? =
        withRetriever(context, uri) { retriever ->
            retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
        }

    private fun MediaMetadataRetriever.durationMs(): Long =
        extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

    private fun MediaMetadataRetriever.meta(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0

    /** Decode size, honouring rotation so portrait clips stay portrait. */
    private fun MediaMetadataRetriever.targetSize(): Pair<Int, Int> {
        val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val upright = if (rotation == 90 || rotation == 270) height to width else width to height
        if (upright.first <= 0 || upright.second <= 0) return config.maxFrameWidth to 0

        val scale = min(1f, config.maxFrameWidth.toFloat() / upright.first)
        return max(1, (upright.first * scale).roundToInt()) to max(1, (upright.second * scale).roundToInt())
    }

    private fun MediaMetadataRetriever.frameAt(timestampMs: Long, width: Int, height: Int): Bitmap? {
        val timeUs = timestampMs * 1000L
        // OPTION_CLOSEST, never OPTION_CLOSEST_SYNC: snapping to keyframes would return the
        // same frame repeatedly and destroy the tracking.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && width > 0 && height > 0) {
            return getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, width, height)
        }
        // API 26 has no scaled variant; decode full size and shrink.
        val full = getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        if (width <= 0 || height <= 0 || (full.width <= width)) return full
        val scaled = Bitmap.createScaledBitmap(full, width, height, true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    /** MediaMetadataRetriever only became AutoCloseable in API 29, so release by hand. */
    private inline fun <T> withRetriever(context: Context, uri: Uri, body: (MediaMetadataRetriever) -> T): T {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return body(retriever)
        } finally {
            retriever.release()
        }
    }
}
