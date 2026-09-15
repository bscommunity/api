package org.bscm.models.dto.theme

import kotlinx.serialization.Serializable
import org.bscm.models.dto.contributor.SimplifiedContributor

@Serializable
data class CreateThemeRequest(
    val name: String,
    val description: String? = null,
    val replaces: String,
    val originalArtwork: String? = null,
    val contributors: List<SimplifiedContributor> = emptyList(),
)