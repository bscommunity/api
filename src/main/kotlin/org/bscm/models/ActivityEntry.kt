package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ActivityType
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class ActivityEntry(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val type: ActivityType,
    val targetId: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)