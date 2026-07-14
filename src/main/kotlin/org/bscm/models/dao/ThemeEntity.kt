package org.bscm.models.dao

import org.bscm.models.tables.ThemeTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class ThemeEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, ThemeEntity>(ThemeTable)

    var name by ThemeTable.name
    var replaces by ThemeTable.replaces
    var displayArtUrl by ThemeTable.displayArtUrl
    var previewUrl by ThemeTable.previewUrl
    var coverUrl by ThemeTable.coverUrl
}
