package org.bscm.models.dto.theme

import kotlinx.serialization.Serializable

@Serializable
data class UpdateThemeRequest(
    val name: String? = null,
    val description: String? = null,
    val replaces: String? = null,
    val originalArtwork: String? = null,
    val previewUrl: String? = null
)