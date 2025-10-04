package org.bscm.models.dao

import org.bscm.models.tables.CollectionItemTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class CollectionItemEntity(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, CollectionItemEntity>(CollectionItemTable)

    var collection by CollectionEntity referencedOn CollectionItemTable.collectionId
    var content by ContentEntity referencedOn CollectionItemTable.contentId

    var addedAt by CollectionItemTable.addedAt
}
