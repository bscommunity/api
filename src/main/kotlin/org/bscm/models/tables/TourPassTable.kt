package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object TourPassTable : IdTable<String>("tour_passes") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId().uniqueIndex()

    val name = varchar("name", 255)
    val description = varchar("description", 500).nullable()
    val artist = varchar("artist", 255).nullable()
    val coverId = varchar("cover_id", 10).nullable()
}
