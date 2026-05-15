@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CatalogItem {
    val id: String
    val contentId: String

    val type: CatalogItemType
    val status: CatalogItemStatus

    val isPublic: Boolean
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
}