package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object NotificationTable : LongIdTable("notifications") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val actorId = reference("actor_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 50)
    val catalogItemId = reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE).nullable()
    val message = text("message")
    val createdAt = datetime("created_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }

    init {
        index(false, userId, createdAt)
    }
}
