package com.iykyk.facecollage

import com.iykyk.facecollage.pipeline.PipelineConfig
import com.iykyk.facecollage.pipeline.Tracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerTest {

    private val tracker = Tracker(PipelineConfig())

    @Test
    fun `one person visible across consecutive frames is a single track`() {
        val frames = (0 until 8).map { f -> listOf(face(frame = f, person = 1, box = boxAt(360f + f * 5, 640f))) }

        val tracks = tracker.buildTracks(frames)

        assertEquals(1, tracks.size)
        assertEquals(8, tracks.single().detections.size)
    }

    @Test
    fun `two people sharing the frame produce two separate tracks`() {
        val frames = (0 until 6).map { f ->
            listOf(
                face(frame = f, person = 1, box = boxAt(200f, 600f)),
                face(frame = f, person = 2, box = boxAt(540f, 600f)),
            )
        }

        val tracks = tracker.buildTracks(frames)

        assertEquals(2, tracks.size)
        assertTrue(tracks.all { it.detections.size == 6 })
    }

    @Test
    fun `a long absence splits one person into two tracks`() {
        // present 0..5, gone 6..11 (6 frames, well past maxGapFrames), back 12..17
        val frames = (0 until 18).map { f ->
            if (f in 6..11) emptyList() else listOf(face(frame = f, person = 1))
        }

        val tracks = tracker.buildTracks(frames)

        assertEquals(2, tracks.size)
        assertEquals(6, tracks[0].detections.size)
        assertEquals(6, tracks[1].detections.size)
    }

    @Test
    fun `a brief dropout inside the gap tolerance keeps one track`() {
        // missing only frame 3, which is within maxGapFrames
        val frames = (0 until 8).map { f ->
            if (f == 3) emptyList() else listOf(face(frame = f, person = 1))
        }

        val tracks = tracker.buildTracks(frames)

        assertEquals(1, tracks.size)
        assertEquals(7, tracks.single().detections.size)
    }

    @Test
    fun `a single-frame flicker is discarded, not counted as an appearance`() {
        val frames = (0 until 6).map { f ->
            if (f == 2) listOf(face(frame = f, person = 7, box = boxAt(100f, 200f))) else emptyList()
        }

        assertEquals(0, tracker.buildTracks(frames).size)
    }

    @Test
    fun `two different people at the same position never merge into one track`() {
        // person 1 holds the spot, then person 2 takes exactly the same box.
        // Each run is long enough to clear minTrackDetections, so this tests identity
        // separation rather than the short-segment filter.
        val box = boxAt(360f, 640f)
        val frames = (0 until 12).map { f ->
            listOf(face(frame = f, person = if (f < 6) 1 else 2, box = box))
        }

        val tracks = tracker.buildTracks(frames)

        assertEquals(2, tracks.size)
        assertTrue(tracks.all { it.detections.size == 6 })
    }

    @Test
    fun `tracks carry their real start and end timestamps`() {
        val frames = (0 until 5).map { f -> listOf(face(frame = f, person = 1)) }

        val track = tracker.buildTracks(frames).single()

        assertEquals(0L, track.startMs)
        assertEquals(4 * SAMPLE_MS, track.endMs)
    }

    @Test
    fun `crossing paths keeps identities apart rather than swapping them`() {
        // two people walk through each other; embeddings must win over pure position
        val frames = (0 until 8).map { f ->
            listOf(
                face(frame = f, person = 1, box = boxAt(150f + f * 60, 640f)),
                face(frame = f, person = 2, box = boxAt(570f - f * 60, 640f)),
            )
        }

        val tracks = tracker.buildTracks(frames)

        assertEquals(2, tracks.size)
        // each track must be internally consistent: all detections from one person
        for (t in tracks) {
            val first = t.detections.first().embedding
            assertTrue(
                t.detections.all { com.iykyk.facecollage.pipeline.cosineSimilarity(first, it.embedding) > 0.8f }
            )
        }
    }
}
