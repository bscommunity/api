package org.bscm.models.dto.collection

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bscm.models.enums.ActionType
import org.bscm.models.enums.CollectionKind
import org.bscm.serialization.LocalDateTimeSerializer

@Serializable
data class BatchCollectionItemRequest(
    val catalogId: String,
    val collectionId: String?,
    val collectionKind: CollectionKind,
    val action: ActionType,
    @Serializable(with = LocalDateTimeSerializer::class)
    val enqueuedAt: LocalDateTime? = null
)