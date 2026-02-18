package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable

@Serializable
data class BatchCollectionItemRequest(
    val items: List<CreateCollectionItemRequest>
)

@Serializable
data class BatchCollectionItemResponse(
    val successful: Int,
    val failed: Int,
    val errors: List<BatchItemError> = emptyList()
)

@Serializable
data class BatchItemError(
    val contentId: String,
    val reason: String
)

