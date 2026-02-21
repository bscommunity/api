package org.bscm.models.dao

import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class CollectionEntity(id: EntityID<UUID>) : Entity<UUID>(id) {
    companion object : EntityClass<UUID, CollectionEntity>(CollectionTable)

    var user by UserEntity referencedOn CollectionTable.userId
    var kind by CollectionTable.kind
    var name by CollectionTable.name
    var slug by CollectionTable.slug
    var isPublic by CollectionTable.isPublic
    var createdAt by CollectionTable.createdAt
    var updatedAt by CollectionTable.updatedAt

    val items by CollectionItemEntity referrersOn CollectionItemTable.collectionId
}
