package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.StreamingLink
import org.bscm.models.enums.Difficulty

@Serializable
data class CreateChartRequest (
    val artist: String,
    val track: String,
    val album: String?,
    val trackUrls: List<StreamingLink>,
    val trackPreviewUrl: String,
    val coverUrl: String,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,

    // First version properties
    val chartUrl: String,
    val chartPreviewUrl: String,
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
)