package org.bscm.models.dto.version

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty

@Serializable
data class SimplifiedVersion(
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
)