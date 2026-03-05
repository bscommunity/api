package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable

@Serializable
data class BatchCollectionItemResponse(
    val successful: Int,
    val failed: Int
)