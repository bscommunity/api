package org.bscm.models.dao

import org.bscm.models.tables.CollectionItemTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class CollectionItemEntity(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, CollectionItemEntity>(CollectionItemTable)

    var collection by CollectionEntity referencedOn CollectionItemTable.collectionId
    var catalogItem by CatalogItemEntity referencedOn CollectionItemTable.catalogId

    var addedAt by CollectionItemTable.addedAt
}
