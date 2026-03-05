package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ActionType
import org.bscm.models.enums.CollectionKind
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class BatchCollectionItemRequest(
    val contentId: String,
    val collectionId: String?,
    val collectionKind: CollectionKind,
    val action: ActionType,
    @Serializable(with = LocalDateTimeSerializer::class)
    val enqueuedAt: LocalDateTime? = null
)