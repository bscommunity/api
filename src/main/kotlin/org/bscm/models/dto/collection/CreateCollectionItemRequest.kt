package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ActionOption

@Serializable
data class CreateCollectionItemRequest(
    val contentId: String,
    val collectionId: String,
    val action: ActionOption
)