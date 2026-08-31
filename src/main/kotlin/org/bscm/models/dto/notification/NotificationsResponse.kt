package org.bscm.models.dto.notification

import kotlinx.serialization.Serializable
import org.bscm.models.Notification

@Serializable
data class NotificationsResponse(
    val items: List<Notification>,
    val unreadCount: Int
)
