package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCollectionItemRequest(
    val contentId: String,
)