package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ContentType
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class CollectionItem(
    val id: Int,
    val collectionId: ULong,
    val contentType: ContentType,
    val contentId: ULong,
    @Serializable(with = LocalDateTimeSerializer::class)
    val addedAt: LocalDateTime
)
