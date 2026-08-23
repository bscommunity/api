package org.bscm.models.dto.theme

import kotlinx.serialization.Serializable

@Serializable
data class CreateThemeRequest(
    val name: String,
    val description: String? = null,
    val replaces: String,
    val originalArtwork: String? = null,
    val previewUrl: String? = null
)