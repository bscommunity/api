@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
sealed interface CatalogItem {
    @Serializable(with = LocalDateTimeSerializer::class)
    val id: String
    val contentId: String
    val coverUrl: String
    val isPublic: Boolean
    val isFeatured: Boolean
    val downloadsSum: Int // Aggregated field

    val contributors: List<Contributor>

    val createdAt: LocalDateTime
    val updatedAt: LocalDateTime

    val likedAt: LocalDateTime? // Derived field
    val bookmarkedAt: LocalDateTime? // Derived field
}