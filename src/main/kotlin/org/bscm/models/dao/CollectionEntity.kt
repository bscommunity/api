package org.bscm.models.dao

import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class CollectionEntity(id: EntityID<ULong>) : Entity<ULong>(id) {
    companion object : EntityClass<ULong, CollectionEntity>(CollectionTable)

    var user by UserEntity referencedOn CollectionTable.userId
    var name by CollectionTable.name
    var isPublic by CollectionTable.isPublic
    var createdAt by CollectionTable.createdAt
    var updatedAt by CollectionTable.updatedAt

    val items by CollectionItemEntity referrersOn CollectionItemTable.collectionId
}
