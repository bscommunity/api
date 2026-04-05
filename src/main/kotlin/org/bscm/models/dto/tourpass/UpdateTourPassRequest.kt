package org.bscm.models.dto.tourpass

import kotlinx.serialization.Serializable

@Serializable
data class UpdateTourPassRequest(
    val name: String? = null,
    val description: String? = null,
    val artist: String? = null,
    val coverUrl: String? = null,
    val chartIds: List<String>? = null
)