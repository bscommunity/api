package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
sealed interface CatalogItem {
    val id: String
    val contentId: String
    val coverUrl: String
    val isPublic: Boolean
    val isFeatured: Boolean

    // Aggregated/derived fields useful for queries
    val downloadsSum: Int
    val isLiked: Boolean
    val isFavorited: Boolean

    val contributors: List<Contributor>

    @Serializable(with = LocalDateTimeSerializer::class)
    val latestPublishedAt: LocalDateTime
}