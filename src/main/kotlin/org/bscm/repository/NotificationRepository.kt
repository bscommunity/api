package org.bscm.repository

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.bscm.models.Notification
import org.bscm.models.NotificationMessage
import org.bscm.models.dao.UserEntity
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.interfaces.INotificationRepository
import org.bscm.models.tables.CatalogItemTable
import org.bscm.models.tables.NotificationTable
import org.bscm.models.tables.UserTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Clock

class NotificationRepository : INotificationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createNotification(
        userId: UUID,
        actorId: UUID,
        type: String,
        catalogItemId: String?,
        message: NotificationMessage
    ): Notification = suspendTransaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val messageJson = json.encodeToString(NotificationMessage.serializer(), message)

        val id = NotificationTable.insertAndGetId {
            it[NotificationTable.userId] = userId
            it[NotificationTable.actorId] = actorId
            it[NotificationTable.type] = type
            it[NotificationTable.catalogItemId] = catalogItemId?.let { cid ->
                EntityID(cid, CatalogItemTable)
            }
            it[NotificationTable.message] = messageJson
            it[NotificationTable.createdAt] = now
        }

        val actor = UserEntity[actorId]
        Notification(
            id = id.value,
            type = type,
            actor = SimplifiedUser(
                id = actor.id.value,
                username = actor.username,
                avatarUrl = actor.avatarUrl,
                bannerUrl = actor.bannerUrl,
                isVerified = actor.isVerified,
                bio = actor.bio,
                accentColor = actor.accentColor,
            ),
            catalogItemId = catalogItemId,
            message = message,
            createdAt = now
        )
    }

    override suspend fun getNotifications(userId: UUID, limit: Int, offset: Int): List<Notification> = suspendTransaction {
        NotificationTable
            .innerJoin(UserTable, { NotificationTable.actorId }, { UserTable.id })
            .select(NotificationTable.columns + UserTable.columns)
            .where { NotificationTable.userId eq userId }
            .orderBy(NotificationTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { row ->
                val actor = UserEntity.wrapRow(row)
                val messageJson = row[NotificationTable.message]
                val message = json.decodeFromString(NotificationMessage.serializer(), messageJson)
                Notification(
                    id = row[NotificationTable.id].value,
                    type = row[NotificationTable.type],
                    actor = SimplifiedUser(
                        id = actor.id.value,
                        username = actor.username,
                        avatarUrl = actor.avatarUrl,
                        bannerUrl = actor.bannerUrl,
                        isVerified = actor.isVerified,
                        bio = actor.bio,
                        accentColor = actor.accentColor,
                    ),
                    catalogItemId = row[NotificationTable.catalogItemId]?.value,
                    message = message,
                    createdAt = row[NotificationTable.createdAt]
                )
            }
    }

    override suspend fun getUnreadCount(userId: UUID): Int = suspendTransaction {
        NotificationTable
            .select(NotificationTable.id)
            .where { NotificationTable.userId eq userId }
            .count()
            .toInt()
    }

    override suspend fun deleteNotification(userId: UUID, notificationId: Long): Boolean = suspendTransaction {
        val deleted = NotificationTable.deleteWhere {
            (NotificationTable.id eq notificationId) and (NotificationTable.userId eq userId)
        }
        deleted > 0
    }

    override suspend fun deleteAllNotifications(userId: UUID): Int = suspendTransaction {
        NotificationTable.deleteWhere {
            NotificationTable.userId eq userId
        }
    }
}
