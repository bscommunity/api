package org.bscm.models.dto.user

import kotlinx.serialization.Serializable

@Serializable
data class UserStats(
    val totalCharts: Int,
    val totalCollections: Int,
    val totalTourpasses: Int = 0, // Future-proof for tour passes
    val totalThemes: Int = 0 // Future-proof for themes
)