package org.bscm.models.dao

import org.bscm.models.tables.ContentTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ContentEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, ContentEntity>(ContentTable)

    var type by ContentTable.type
    val createdAt by ContentTable.createdAt
    var updatedAt by ContentTable.updatedAt
}
