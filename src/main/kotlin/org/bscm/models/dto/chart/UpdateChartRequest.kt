package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Visibility

/**
 * User-editable chart fields only.
 *
 * Track metadata (title, artist, album, genres, bpm) is bound at creation,
 * shared system-wide across charts, and never edited. Difficulty and deluxe
 * come from each uploaded bundle version. Featured is system-managed.
 */
@Serializable
data class UpdateChartRequest (
    val isExplicit: Boolean? = null,
    val visibility: Visibility? = null,
    val previewVideoId: String? = null,
)