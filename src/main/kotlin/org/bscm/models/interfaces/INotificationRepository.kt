package org.bscm.models.interfaces

import org.bscm.models.Notification
import org.bscm.models.NotificationMessage
import java.util.*

interface INotificationRepository {
    suspend fun createNotification(
        userId: UUID,
        actorId: UUID,
        type: String,
        catalogItemId: String?,
        message: NotificationMessage
    ): Notification

    suspend fun getNotifications(userId: UUID, limit: Int, offset: Int): List<Notification>

    suspend fun getUnreadCount(userId: UUID): Int

    suspend fun deleteNotification(userId: UUID, notificationId: Long): Boolean

    suspend fun deleteAllNotifications(userId: UUID): Int
}
