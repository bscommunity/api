package org.bscm.models.dto.notification

import kotlinx.serialization.Serializable

@Serializable
data class UnreadCountResponse(
    val unreadCount: Int
)
