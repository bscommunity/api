package org.bscm.models.dto.tourpass

import kotlinx.serialization.Serializable
import org.bscm.models.StreamingLink

@Serializable
data class CreateTourPassRequest(
    val name: String,
    val description: String? = null,
    val artist: String?,
    val coverUrl: String? = null,
    val previewUrl: String? = null,
    val chartIds: List<String>? = null,
    val playlistUrls: List<StreamingLink>? = null,
)