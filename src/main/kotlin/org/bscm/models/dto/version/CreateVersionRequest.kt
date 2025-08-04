package org.bscm.models.dto.version

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty

@Serializable
data class CreateVersionRequest(
    val id: ULong? = null,
    val track: String, // for comparison purposes
    val artist: String, // for comparison purposes
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val bundleUrl: String,
    val previewUrl: String? = null,
)