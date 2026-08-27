package org.bscm.models.dto.version

import kotlinx.serialization.Serializable

@Serializable
data class CreateVersionRequest(
    val changelog: String = "",
)
