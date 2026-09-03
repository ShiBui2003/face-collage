package com.iykyk.facecollage.data

import android.graphics.Bitmap

/** What the pipeline is doing right now. The UI renders directly from this. */
sealed interface ProcessingState {

    data object Idle : ProcessingState

    /**
     * [fraction] is 0..1 overall progress, or null when the stage cannot report progress.
     * [label] is short, friendly and shown as-is.
     */
    data class Working(
        val stage: Stage,
        val label: String,
        val fraction: Float?,
    ) : ProcessingState

    data class Done(val result: CollageResult) : ProcessingState

    data class Failed(val message: String) : ProcessingState

    enum class Stage { EXTRACTING, DETECTING, CLUSTERING, SCORING, BUILDING }
}

/** One person as shown in the results grid. */
data class PersonResult(
    val identityId: Int,
    val appearanceCount: Int,
    /** Generously cropped shot, never tight to the face box. */
    val portrait: Bitmap,
    /** Segment ranges, used by the debug panel to eyeball counts against a known answer. */
    val appearances: List<Appearance>,
)

data class CollageResult(
    val people: List<PersonResult>,
    /** The shareable collage, one tile per person. */
    val collage: Bitmap,
    val videoDurationMs: Long,
    val framesAnalysed: Int,
    val facesDetected: Int,
) {
    val totalAppearances: Int get() = people.sumOf { it.appearanceCount }
}
