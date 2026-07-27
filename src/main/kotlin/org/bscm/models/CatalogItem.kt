@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.Visibility
import org.bscm.serialization.LocalDateTimeSerializer
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("itemKind")
sealed interface CatalogItem {
    val id: String

    val type: CatalogItemType
    val status: CatalogItemStatus
    val visibility: Visibility

    val isFeatured: Boolean

    val downloadsSum: Int

    val contributors: List<Contributor>

    val createdAt: LocalDateTime
    val publishedAt: LocalDateTime?
    val updatedAt: LocalDateTime?

    // Derived fields
    val likedAt: LocalDateTime?
    val bookmarkedAt: LocalDateTime?

    // Optional external preview video
    val previewVideoId: String?

    // Discord publishing coordinates (fixed per item)
    val discordChannelId: String?
    val discordMessageId: String?

    // Author (nullable — ON DELETE SET NULL)
    val authorId: UUID?
}