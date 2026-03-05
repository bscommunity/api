package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class Badge(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val description: String?,
    val criteria: String?,
    @Serializable (with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
)