package org.bscm.models.dao

import org.bscm.models.tables.NotificationTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class NotificationEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<NotificationEntity>(NotificationTable)

    var user by UserEntity referencedOn NotificationTable.userId
    var actor by UserEntity referencedOn NotificationTable.actorId
    var type by NotificationTable.type
    var catalogItem by CatalogItemEntity optionalReferencedOn NotificationTable.catalogItemId
    var message by NotificationTable.message
    val createdAt by NotificationTable.createdAt
}
