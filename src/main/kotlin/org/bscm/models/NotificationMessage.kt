package org.bscm.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.bscm.models.enums.CatalogItemType

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface NotificationMessage {
    @Serializable
    @SerialName("contributor_added")
    data class ContributorAdded(
        val actorName: String,
        val itemType: CatalogItemType,
        val itemName: String
    ) : NotificationMessage
}
