package org.bscm.models.dto.theme

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Visibility

@Serializable
data class UpdateThemeRequest(
    val name: String? = null,
    val replaces: String? = null,
    val originalArtwork: String? = null,
    val previewVideoId: String? = null,
    val visibility: Visibility? = null,
    val isFeatured: Boolean? = null,
)