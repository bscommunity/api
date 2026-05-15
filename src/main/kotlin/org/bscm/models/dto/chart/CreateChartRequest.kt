package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.StreamingRef
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre

@Serializable
data class CreateChartRequest (
    val artist: String,
    val track: String,
    val album: String? = null,
    val trackUrls: List<StreamingRef>,
    val previewUrl: String? = null,
    val trackPreviewUrl: String? = null,
    val coverUrl: String,
    val genre: Genre? = null,
    val isExplicit: Boolean,

    // Server-side properties
    val id: ULong? = null,
    val versionId: ULong? = null,
    val contentId: String? = null,

    // First version properties
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val bundleUrl: String,
)
