package org.bscm.models.dto.collection

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ActionType

@Serializable
data class CreateCollectionItemRequest(
    val contentId: String,
    val action: ActionType
)