package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCollectionRequest(
    val name: String? = null,
    val isPublic: Boolean? = null
)