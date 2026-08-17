package org.bscm.models.dto.theme

import kotlinx.serialization.Serializable

@Serializable
data class CreateThemeVersionRequest(
    val id: ULong? = null,
    val bundleUrl: String,
    val fileSizeBytes: Long = 0,
    val changelog: List<String> = emptyList(),
)
