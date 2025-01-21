package org.bscm.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateChartRequest (
    val artist: String? = null,
    val name: String? = null,
    val coverUrl: String? = null,
    val duration: Int? = null,
    val notesAmount: Int? = null,
    val isDeluxe: Boolean? = null,
    val isExplicit: Boolean? = null,
    val isFeatured: Boolean? = null,
)