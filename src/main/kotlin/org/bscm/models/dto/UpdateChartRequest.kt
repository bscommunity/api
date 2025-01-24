package org.bscm.models.dto

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty

@Serializable
data class UpdateChartRequest (
    val artist: String? = null,
    val track: String? = null,
    val coverUrl: String? = null,
    val difficulty: Difficulty? = null,
    val isDeluxe: Boolean? = null,
    val isExplicit: Boolean? = null,
    val isFeatured: Boolean? = null,
)