package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ActionOption

@Serializable
data class CreateCollectionItemRequest(
    val contentId: ULong,
    val collectionId: ULong,
    val action: ActionOption
)