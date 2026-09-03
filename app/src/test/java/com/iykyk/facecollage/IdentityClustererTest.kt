package com.iykyk.facecollage

import com.iykyk.facecollage.data.DetectedFace
import com.iykyk.facecollage.data.Track
import com.iykyk.facecollage.pipeline.IdentityClusterer
import com.iykyk.facecollage.pipeline.PipelineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityClustererTest {

    private val clusterer = IdentityClusterer(PipelineConfig())

    /** A track of [count] detections for [person], starting at [startMs]. */
    private fun track(id: Int, person: Int, startMs: Long, count: Int = 4): Track {
        val detections: List<DetectedFace> = (0 until count).map { i ->
            face(
                frame = ((startMs / SAMPLE_MS).toInt() + i),
                person = person,
                variant = person * 1000 + id * 10 + i,
                timestampMs = startMs + i * SAMPLE_MS,
            )
        }
        return Track(id = id, detections = detections)
    }

    @Test
    fun `two tracks of one person far apart in time merge into one identity with two appearances`() {
        val tracks = listOf(track(0, person = 1, startMs = 0), track(1, person = 1, startMs = 10_000))

        val identities = clusterer.cluster(tracks)

        assertEquals(1, identities.size)
        assertEquals(2, identities.single().appearanceCount)
    }

    @Test
    fun `tracks overlapping in time are never merged, even with identical embeddings`() {
        // the crux of the two-people-share-a-frame case: one person cannot be in two places at once
        val a = track(0, person = 1, startMs = 10_100)
        val b = track(1, person = 1, startMs = 10_200)

        val identities = clusterer.cluster(listOf(a, b))

        assertEquals(2, identities.size)
    }

    @Test
    fun `different people stay separate identities`() {
        val tracks = listOf(
            track(0, person = 1, startMs = 0),
            track(1, person = 2, startMs = 5_000),
            track(2, person = 3, startMs = 10_000),
        )

        assertEquals(3, clusterer.cluster(tracks).size)
    }

    @Test
    fun `two tracks separated by less than the coalesce gap count as one appearance`() {
        // one continuous appearance that briefly broke: ends at 375ms, resumes at 500ms (125ms gap)
        val a = track(0, person = 1, startMs = 0, count = 4)
        val b = track(1, person = 1, startMs = 500, count = 4)

        val identity = clusterer.cluster(listOf(a, b)).single()

        assertEquals(1, identity.appearanceCount)
        assertEquals(listOf(0, 1), identity.appearances.single().trackIds)
    }

    @Test
    fun `two tracks separated by more than the coalesce gap stay separate appearances`() {
        // A gap wider than the tracker's own break threshold means the person really did leave.
        // Coalescing these would weld two distinct appearances into one and undercount.
        val config = PipelineConfig()
        val a = track(0, person = 1, startMs = 0, count = 4)
        val b = track(1, person = 1, startMs = a.endMs + config.trackBreakGapMs + 100, count = 4)

        val identity = clusterer.cluster(listOf(a, b)).single()

        assertEquals(2, identity.appearanceCount)
    }

    @Test
    fun `a gap just above the coalesce threshold is not merged`() {
        val config = PipelineConfig()
        val a = track(0, person = 1, startMs = 0, count = 4)
        val gap = config.appearanceCoalesceGapMs + SAMPLE_MS
        val b = track(1, person = 1, startMs = a.endMs + gap, count = 4)

        assertEquals(2, clusterer.cluster(listOf(a, b)).single().appearanceCount)
    }

    @Test
    fun `appearances are reported in chronological order with real time ranges`() {
        val tracks = listOf(track(1, person = 1, startMs = 20_000), track(0, person = 1, startMs = 0))

        val identity = clusterer.cluster(tracks).single()

        assertEquals(2, identity.appearanceCount)
        assertEquals(0L, identity.appearances.first().startMs)
        assertEquals(20_000L + 3 * SAMPLE_MS, identity.appearances.last().endMs)
    }

    @Test
    fun `sample-1 shaped input yields five identities with four appearances each`() {
        // Structural rehearsal of the known ground truth, on synthetic data:
        // five people, four well-separated appearances each, including two overlap windows.
        var id = 0
        val tracks = buildList {
            for (person in 0 until 5) {
                for (appearance in 0 until 4) {
                    add(track(id++, person = person, startMs = appearance * 6_000L + person * 700L))
                }
            }
        }

        val identities = clusterer.cluster(tracks)

        assertEquals(5, identities.size)
        assertTrue(
            "appearance counts were ${identities.map { it.appearanceCount }}",
            identities.all { it.appearanceCount == 4 },
        )
        assertEquals(20, identities.sumOf { it.appearanceCount })
    }

    @Test
    fun `empty input produces no identities`() {
        assertEquals(0, clusterer.cluster(emptyList()).size)
    }
}
