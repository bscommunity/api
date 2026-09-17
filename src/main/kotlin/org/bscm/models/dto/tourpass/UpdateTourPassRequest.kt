package org.bscm.models.dto.tourpass

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Visibility

@Serializable
data class UpdateTourPassRequest(
    val name: String? = null,
    val description: String? = null,
    val artist: String? = null,
    val chartIds: List<String>? = null,
    val previewVideoId: String? = null,
    val visibility: Visibility? = null,
    val isFeatured: Boolean? = null,
)