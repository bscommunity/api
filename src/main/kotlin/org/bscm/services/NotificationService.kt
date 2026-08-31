package org.bscm.services

import org.bscm.models.Notification
import org.bscm.models.dao.UserEntity
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.NotificationType
import org.bscm.models.interfaces.INotificationRepository
import org.bscm.models.tables.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class NotificationService(
    private val notificationRepository: INotificationRepository
) {

    suspend fun getNotifications(userId: UUID, limit: Int, offset: Int): List<Notification> {
        return notificationRepository.getNotifications(userId, limit, offset)
    }

    suspend fun getUnreadCount(userId: UUID): Int {
        return notificationRepository.getUnreadCount(userId)
    }

    suspend fun deleteNotification(userId: UUID, notificationId: Long): Boolean {
        return notificationRepository.deleteNotification(userId, notificationId)
    }

    suspend fun deleteAllNotifications(userId: UUID): Int {
        return notificationRepository.deleteAllNotifications(userId)
    }

    suspend fun notifyContributorAdded(
        catalogItemId: String,
        actorId: UUID,
        recipientIds: List<UUID>
    ) {
        if (recipientIds.isEmpty()) return

        val itemInfo = resolveCatalogItemInfo(catalogItemId) ?: return
        val typeName = itemInfo.first.lowercase().replace("_", " ")
        val itemName = itemInfo.second

        val actorName = resolveUsername(actorId) ?: return
        val message = "@$actorName shared a $typeName ($itemName) with you"

        for (recipientId in recipientIds) {
            if (recipientId == actorId) continue
            notificationRepository.createNotification(
                userId = recipientId,
                actorId = actorId,
                type = NotificationType.CONTRIBUTOR_ADDED.name,
                catalogItemId = catalogItemId,
                message = message
            )
        }
    }

    private suspend fun resolveCatalogItemInfo(catalogItemId: String): Pair<String, String>? = suspendTransaction {
        val row = CatalogItemTable
            .selectAll()
            .where { CatalogItemTable.id eq catalogItemId }
            .firstOrNull() ?: return@suspendTransaction null

        val type = row[CatalogItemTable.type]
        val name = when (type) {
            CatalogItemType.CHART -> {
                val chartRow = ChartTable
                    .innerJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
                    .select(TrackTable.title, TrackTable.artist)
                    .where { ChartTable.id eq catalogItemId }
                    .firstOrNull()
                chartRow?.let { "${it[TrackTable.artist]} - ${it[TrackTable.title]}" } ?: "Unknown Chart"
            }
            CatalogItemType.TOUR_PASS -> {
                val tpRow = TourPassTable
                    .selectAll()
                    .where { TourPassTable.id eq catalogItemId }
                    .firstOrNull()
                tpRow?.get(TourPassTable.name) ?: "Unknown Tour Pass"
            }
            CatalogItemType.THEME -> {
                val themeRow = ThemeTable
                    .selectAll()
                    .where { ThemeTable.id eq catalogItemId }
                    .firstOrNull()
                themeRow?.get(ThemeTable.name) ?: "Unknown Theme"
            }
        }

        Pair(type.name, name)
    }

    private suspend fun resolveUsername(userId: UUID): String? = suspendTransaction {
        UserEntity.findById(userId)?.username
    }
}
