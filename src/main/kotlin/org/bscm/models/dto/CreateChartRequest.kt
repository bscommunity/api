package org.bscm.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateChartRequest (
    val artist: String,
    val name: String,
    val coverUrl: String,
    val duration: Int,
    val notesAmount: Int,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val isFeatured: Boolean,
)