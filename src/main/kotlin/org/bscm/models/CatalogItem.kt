package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

interface CatalogItem {
    val id: String
    val shareId: String
    val coverUrl: String
    val isPublic: Boolean
    val isFeatured: Boolean

    // Aggregated/derived fields useful for queries
    val downloadsSum: Int

    @Serializable(with = LocalDateTimeSerializer::class)
    val latestPublishedAt: LocalDateTime
}