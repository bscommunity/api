package org.bscm.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.serialization.LocalDateTimeSerializer

@Serializable
data class Notification(
    val id: Long,
    val type: String,
    val actor: SimplifiedUser,
    val catalogItemId: String?,
    val message: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)
