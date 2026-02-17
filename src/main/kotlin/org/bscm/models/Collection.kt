@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.CollectionKind
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

// SS = Server-side gathered fields for convenience

@Serializable
data class Collection(
    val id: UUID,
    val userId: UUID,
    val kind: CollectionKind,
    val name: String,
    val isPublic: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val coverUrl: String? = null, // SS
    val itemsCount: Int = 0, // SS
)