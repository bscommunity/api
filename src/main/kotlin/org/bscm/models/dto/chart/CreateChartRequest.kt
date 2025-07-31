package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre

@Serializable
data class CreateChartRequest (
    val id: ULong? = null,
    val artist: String,
    val track: String,
    val album: String? = null,
    val trackUrls: List<CreateStreamingLink>,
    val trackPreviewUrl: String,
    val coverUrl: String,
    val genre: Genre? = null,
    val isExplicit: Boolean,

    // First version properties
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val bundleUrl: String,
    val previewUrl: String,
)