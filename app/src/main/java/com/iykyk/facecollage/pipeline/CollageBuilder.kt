package com.iykyk.facecollage.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.iykyk.facecollage.data.PersonResult
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws the shareable collage: one tile per person, sized for an Instagram Story canvas.
 *
 * Tiles are centre-cropped from the generously cropped portraits the pipeline produced, so
 * nothing here crops tight to a face box.
 */
class CollageBuilder(private val config: PipelineConfig = PipelineConfig()) {

    fun build(people: List<PersonResult>): Bitmap {
        val canvasBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)

        drawBackground(canvas)
        drawHeader(canvas, people)
        if (people.isNotEmpty()) drawTiles(canvas, people)
        drawFooter(canvas, people)
        return canvasBitmap
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(INK, INK_SOFT, INK),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        val blob = Paint().apply { isAntiAlias = true }
        blob.color = withAlpha(BUBBLEGUM, 38)
        canvas.drawCircle(WIDTH * 0.86f, HEIGHT * 0.07f, 260f, blob)
        blob.color = withAlpha(MINT, 32)
        canvas.drawCircle(WIDTH * 0.10f, HEIGHT * 0.95f, 300f, blob)
    }

    private fun drawHeader(canvas: Canvas, people: List<PersonResult>) {
        val title = Paint().apply {
            isAntiAlias = true
            color = CLOUD
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Who's here", MARGIN, 132f, title)

        val accent = Paint().apply {
            isAntiAlias = true
            color = MINT
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(peopleLine(people.size), MARGIN, 196f, accent)
    }

    private fun drawTiles(canvas: Canvas, people: List<PersonResult>) {
        val columns = columnsFor(people.size)
        val rows = ceil(people.size / columns.toFloat()).toInt()

        val gridHeight = HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT
        val cellWidth = (WIDTH - MARGIN * 2 - GAP * (columns - 1)) / columns
        val cellHeight = (gridHeight - GAP * (rows - 1)) / rows

        people.forEachIndexed { index, person ->
            val column = index % columns
            val row = index / columns
            val left = MARGIN + column * (cellWidth + GAP)
            val top = HEADER_HEIGHT + row * (cellHeight + GAP)
            drawTile(canvas, person, RectF(left, top, left + cellWidth, top + cellHeight))
        }
    }

    private fun drawTile(canvas: Canvas, person: PersonResult, bounds: RectF) {
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(bounds, RADIUS, RADIUS, Path.Direction.CW) })
        canvas.drawColor(INK_SOFT)
        drawCentreCropped(canvas, person.portrait, bounds)
        canvas.restore()

        canvas.drawRoundRect(
            bounds, RADIUS, RADIUS,
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = CLOUD
            },
        )
        drawBadge(canvas, person.appearanceCount, bounds)
    }

    /** Fills [bounds] with the largest centred region of [source] that keeps its aspect ratio. */
    private fun drawCentreCropped(canvas: Canvas, source: Bitmap, bounds: RectF) {
        val targetAspect = bounds.width() / bounds.height()
        val sourceAspect = source.width.toFloat() / source.height

        val src = if (sourceAspect > targetAspect) {
            val keepWidth = (source.height * targetAspect).toInt().coerceIn(1, source.width)
            val offset = (source.width - keepWidth) / 2
            Rect(offset, 0, offset + keepWidth, source.height)
        } else {
            // source is taller: trim top and bottom, biased upward so faces stay in frame
            val keepHeight = (source.width / targetAspect).toInt().coerceIn(1, source.height)
            val offset = ((source.height - keepHeight) * FACE_BIAS).toInt().coerceAtLeast(0)
            Rect(0, offset, source.width, (offset + keepHeight).coerceAtMost(source.height))
        }

        canvas.drawBitmap(
            source, src, bounds,
            Paint().apply { isFilterBitmap = true; isAntiAlias = true },
        )
    }

    private fun drawBadge(canvas: Canvas, count: Int, bounds: RectF) {
        val text = "x$count"
        val label = Paint().apply {
            isAntiAlias = true
            color = CLOUD
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val padH = 26f
        val height = 66f
        val right = bounds.right - 20f
        val bottom = bounds.bottom - 20f
        val pill = RectF(right - label.measureText(text) - padH * 2, bottom - height, right, bottom)

        canvas.drawRoundRect(
            pill, height / 2f, height / 2f,
            Paint().apply { isAntiAlias = true; color = BUBBLEGUM },
        )
        canvas.drawText(text, pill.left + padH, pill.bottom - 20f, label)
    }

    private fun drawFooter(canvas: Canvas, people: List<PersonResult>) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = withAlpha(CLOUD, 190)
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val line = if (people.isEmpty()) {
            "No faces found"
        } else {
            "${people.sumOf { it.appearanceCount }} appearances in total"
        }
        canvas.drawText(line, MARGIN, HEIGHT - 64f, paint)
    }

    private fun peopleLine(count: Int): String = when (count) {
        0 -> "nobody in this one"
        1 -> "1 person"
        else -> "$count people"
    }

    /** Keeps tiles close to the canvas aspect: one column for a pair, never more than three. */
    private fun columnsFor(count: Int): Int =
        if (count <= 2) 1 else min(MAX_COLUMNS, ceil(sqrt(count.toDouble())).toInt())

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        // Instagram Story canvas
        const val WIDTH = 1080
        const val HEIGHT = 1920

        const val MARGIN = 48f
        const val GAP = 24f
        const val RADIUS = 44f
        const val HEADER_HEIGHT = 250f
        const val FOOTER_HEIGHT = 120f
        const val MAX_COLUMNS = 3

        /** Crop bias when trimming a tall portrait: faces sit above centre. */
        const val FACE_BIAS = 0.28f

        val INK = Color.rgb(0x16, 0x13, 0x2A)
        val INK_SOFT = Color.rgb(0x24, 0x1F, 0x3F)
        val BUBBLEGUM = Color.rgb(0xFF, 0x4D, 0x8D)
        val MINT = Color.rgb(0x35, 0xE0, 0xC8)
        val CLOUD = Color.rgb(0xFF, 0xF6, 0xEE)
    }
}
