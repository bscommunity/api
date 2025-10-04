package org.bscm.models.dao

import org.bscm.models.tables.ContentTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ContentEntity(id: EntityID<ULong>) : Entity<ULong>(id) {
    companion object : EntityClass<ULong, ContentEntity>(ContentTable)

    val type by ContentTable.type
    val createdAt by ContentTable.createdAt
    val updatedAt by ContentTable.updatedAt
}
