package org.bscm.services

import org.bscm.models.NotificationMessage
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

    suspend fun getNotifications(userId: UUID, limit: Int, offset: Int) =
        notificationRepository.getNotifications(userId, limit, offset)

    suspend fun getUnreadCount(userId: UUID) =
        notificationRepository.getUnreadCount(userId)

    suspend fun deleteNotification(userId: UUID, notificationId: Long) =
        notificationRepository.deleteNotification(userId, notificationId)

    suspend fun deleteAllNotifications(userId: UUID) =
        notificationRepository.deleteAllNotifications(userId)

    suspend fun notifyContributorAdded(
        catalogItemId: String,
        actorId: UUID,
        recipientIds: List<UUID>
    ) {
        if (recipientIds.isEmpty()) return

        val itemInfo = resolveCatalogItemInfo(catalogItemId) ?: return
        val actorName = resolveUsername(actorId) ?: return

        val message = NotificationMessage.ContributorAdded(
            actorName = actorName,
            itemType = itemInfo.first,
            itemName = itemInfo.second
        )

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

    private suspend fun resolveCatalogItemInfo(catalogItemId: String): Pair<CatalogItemType, String>? = suspendTransaction {
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

        Pair(type, name)
    }

    private suspend fun resolveUsername(userId: UUID): String? = suspendTransaction {
        UserEntity.findById(userId)?.username
    }
}
