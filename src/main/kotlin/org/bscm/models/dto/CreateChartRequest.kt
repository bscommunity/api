package org.bscm.models.dto

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty

@Serializable
data class CreateChartRequest (
    val artist: String,
    val track: String,
    val coverUrl: String,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val isFeatured: Boolean,

    // First version properties
    val chartUrl: String,
    val duration: Int,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
)