package org.bscm.models.dao

import org.bscm.models.tables.CatalogItemTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ContentEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, ContentEntity>(CatalogItemTable)

    var type by CatalogItemTable.type
    val createdAt by CatalogItemTable.createdAt
}
