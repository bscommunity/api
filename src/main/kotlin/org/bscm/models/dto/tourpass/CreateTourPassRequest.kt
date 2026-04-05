package org.bscm.models.dto.tourpass

import kotlinx.serialization.Serializable

@Serializable
data class CreateTourPassRequest(
    val name: String,
    val description: String? = null,
    val artist: String?,
    val coverUrl: String? = null,
    val chartIds: List<String>? = null
)