@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.repository.ContentType
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class UserInteraction(
    val userId: UUID,
    val contentType: ContentType,
    val contentId: ULong,
    val likedAt: LocalDateTime?,
    val favoritedAt: LocalDateTime?
)