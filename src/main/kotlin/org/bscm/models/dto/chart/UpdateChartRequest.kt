package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.enums.Visibility

@Serializable
data class UpdateChartRequest (
    val artist: String? = null,
    val track: String? = null,
    val album: String? = null,
    val coverUrl: String? = null,
    val difficulty: Difficulty? = null,
    val genres: List<Genre>? = null,
    val isDeluxe: Boolean? = null,
    val isExplicit: Boolean? = null,
    val isFeatured: Boolean? = null,
    val visibility: Visibility? = null,
    val previewVideoId: String? = null,
)