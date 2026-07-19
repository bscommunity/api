package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object TourPassTable : IdTable<String>("tour_passes") {
    override val id: Column<EntityID<String>> = varchar("id", 10).entityId()

    val name = varchar("name", 255)
    val description = varchar("description", 500).nullable()
    val artist = varchar("artist", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}
